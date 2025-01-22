package com.limechain.sync.warpsync;

import com.limechain.exception.global.RuntimeCodeException;
import com.limechain.exception.trie.TrieDecoderException;
import com.limechain.grandpa.state.GrandpaSetState;
import com.limechain.network.PeerMessageCoordinator;
import com.limechain.network.PeerRequester;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import com.limechain.network.protocol.grandpa.messages.neighbour.NeighbourMessage;
import com.limechain.network.protocol.lightclient.pb.LightClientMessage;
import com.limechain.network.protocol.sync.BlockRequestField;
import com.limechain.network.protocol.sync.pb.SyncMessage.BlockData;
import com.limechain.network.protocol.warp.DigestHelper;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.Justification;
import com.limechain.network.protocol.warp.scale.reader.BlockHeaderReader;
import com.limechain.network.protocol.warp.scale.reader.JustificationReader;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.RuntimeBuilder;
import com.limechain.storage.DBConstants;
import com.limechain.storage.KVRepository;
import com.limechain.storage.block.SyncState;
import com.limechain.sync.JustificationVerifier;
import com.limechain.trie.decoded.Trie;
import com.limechain.trie.decoded.TrieVerifier;
import com.limechain.utils.LittleEndianUtils;
import com.limechain.utils.StringUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.types.Hash256;
import io.libp2p.core.PeerId;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Singleton class, holds and handles the synced state of the Host.
 */
@Log
@Setter
public class WarpSyncState {

    private final SyncState syncState;
    private final GrandpaSetState grandpaSetState;
    private final PeerRequester requester;
    private final PeerMessageCoordinator messageCoordinator;
    private final KVRepository<String, Object> db;

    public static final String CODE_KEY = StringUtils.toHex(":code");

    @Getter
    private boolean warpSyncFragmentsFinished;
    @Getter
    private boolean warpSyncFinished;

    @Getter
    private Runtime runtime;
    @Getter
    private byte[] runtimeCode;

    protected final RuntimeBuilder runtimeBuilder;
    //TODO Yordan: maybe we won't need this anymore.
    private final Set<BigInteger> scheduledRuntimeUpdateBlocks;

    public WarpSyncState(SyncState syncState,
                         KVRepository<String, Object> db,
                         RuntimeBuilder runtimeBuilder,
                         PeerRequester requester,
                         PeerMessageCoordinator messageCoordinator,
                         GrandpaSetState grandpaSetState) {
        this(syncState,
                grandpaSetState,
                db,
                runtimeBuilder,
                new HashSet<>(),
                requester,
                messageCoordinator
        );
    }

    public WarpSyncState(SyncState syncState, GrandpaSetState grandpaSetState,
                         KVRepository<String, Object> db,
                         RuntimeBuilder runtimeBuilder, Set<BigInteger> scheduledRuntimeUpdateBlocks,
                         PeerRequester requester,
                         PeerMessageCoordinator messageCoordinator) {

        this.syncState = syncState;
        this.grandpaSetState = grandpaSetState;
        this.db = db;
        this.runtimeBuilder = runtimeBuilder;
        this.scheduledRuntimeUpdateBlocks = scheduledRuntimeUpdateBlocks;
        this.requester = requester;
        this.messageCoordinator = messageCoordinator;
    }

    /**
     * Update the state with information from a block announce message.
     * Schedule runtime updates found in header, to be executed when block is verified.
     *
     * @param blockAnnounceMessage received block announce message
     */
    public void syncBlockAnnounce(BlockAnnounceMessage blockAnnounceMessage) {
        boolean hasRuntimeUpdate = Arrays.stream(blockAnnounceMessage.getHeader().getDigest())
                .anyMatch(d -> d.getType() == DigestType.RUN_ENV_UPDATED);

        if (hasRuntimeUpdate) {
            scheduledRuntimeUpdateBlocks.add(blockAnnounceMessage.getHeader().getBlockNumber());
        }
    }

    /**
     * Updates the Host's state with information from a commit message.
     * Synchronized to avoid race condition between checking and updating latest block
     * Scheduled runtime updates for synchronized blocks are executed.
     *
     * @param commitMessage received commit message
     * @param peerId        sender of the message
     */
    public synchronized void syncCommit(CommitMessage commitMessage, PeerId peerId) {
        if (commitMessage.getVote().getBlockNumber().compareTo(syncState.getLastFinalizedBlockNumber()) <= 0) {
            log.log(Level.FINE, String.format("Received commit message for finalized block %d from peer %s",
                    commitMessage.getVote().getBlockNumber(), peerId));
            return;
        }

        log.log(Level.FINE, "Received commit message from peer " + peerId
                + " for block #" + commitMessage.getVote().getBlockNumber()
                + " with hash " + commitMessage.getVote().getBlockHash()
                + " with setId " + commitMessage.getSetId() + " and round " + commitMessage.getRoundNumber()
                + " with " + commitMessage.getPreCommits().length + " voters");

        boolean verified = JustificationVerifier.verify(commitMessage.getPreCommits(), commitMessage.getRoundNumber());
        if (!verified) {
            log.log(Level.WARNING, "Could not verify commit from peer: " + peerId);
            return;
        }

        grandpaSetState.addCommitMessageToArchive(commitMessage);

        if (warpSyncFinished && !grandpaSetState.participatesAsVoter()) {
            updateState(commitMessage);
        }
    }

    private void updateState(CommitMessage commitMessage) {
        BigInteger lastFinalizedBlockNumber = syncState.getLastFinalizedBlockNumber();
        if (commitMessage.getVote().getBlockNumber().compareTo(lastFinalizedBlockNumber) < 1) {
            return;
        }
        syncState.finalizedCommitMessage(commitMessage);

        if (warpSyncFinished && scheduledRuntimeUpdateBlocks.contains(lastFinalizedBlockNumber)) {
            new Thread(this::updateRuntime).start();
        }
    }

    private void updateRuntime() {
        updateRuntimeCode();
        buildRuntime();
        BigInteger lastFinalizedBlockNumber = syncState.getLastFinalizedBlockNumber();
        scheduledRuntimeUpdateBlocks.remove(lastFinalizedBlockNumber);
    }

    private static final byte[] CODE_KEY_BYTES =
            LittleEndianUtils.convertBytes(StringUtils.hexToBytes(StringUtils.toHex(":code")));

    /**
     * Builds and returns the runtime code based on decoded proofs and state root hash.
     *
     * @param decodedProofs The decoded trie proofs.
     * @param stateRoot     The state root hash.
     * @return The runtime code.
     * @throws RuntimeCodeException if an error occurs during the construction of the trie or retrieval of the code.
     */
    public byte[] buildRuntimeCode(byte[][] decodedProofs, Hash256 stateRoot) {
        try {
            Trie trie = TrieVerifier.buildTrie(decodedProofs, stateRoot.getBytes());
            var code = trie.get(CODE_KEY_BYTES);
            if (code == null) {
                throw new RuntimeCodeException("Couldn't retrieve runtime code from trie");
            }
            //TODO Heap pages should be fetched from out storage
            log.log(Level.INFO, "Runtime and heap pages downloaded");
            return code;

        } catch (TrieDecoderException e) {
            throw new RuntimeCodeException("Couldn't build trie from proofs list: " + e.getMessage());
        }
    }

    /**
     * Update the runtime code and heap pages, by requesting the code field of the last finalized block, using the
     * Light Messages protocol.
     */
    public void updateRuntimeCode() {
        Hash256 lastFinalizedBlockHash = syncState.getLastFinalizedBlockHash();
        Hash256 stateRoot = syncState.getStateRoot();

        LightClientMessage.Response response = requester.makeRemoteReadRequest(
                lastFinalizedBlockHash.toString(),
                new String[]{CODE_KEY}
        ).join();

        byte[] proof = response.getRemoteReadResponse().getProof().toByteArray();
        byte[][] decodedProofs = decodeProof(proof);

        this.runtimeCode = buildRuntimeCode(decodedProofs, stateRoot);

        saveRuntimeCode(runtimeCode);
    }

    private byte[][] decodeProof(byte[] proof) {
        ScaleCodecReader reader = new ScaleCodecReader(proof);
        int size = reader.readCompactInt();
        byte[][] decodedProofs = new byte[size][];

        for (int i = 0; i < size; ++i) {
            decodedProofs[i] = reader.readByteArray();
        }
        return decodedProofs;
    }

    private void saveRuntimeCode(byte[] runtimeCode) {
        db.save(DBConstants.RUNTIME_CODE, runtimeCode);
    }

    /**
     * Build the runtime from the available runtime code.
     */
    public void buildRuntime() {
        try {
            runtime = runtimeBuilder.buildRuntime(runtimeCode);
        } catch (UnsatisfiedLinkError e) {
            log.log(Level.SEVERE, "Error loading wasm module");
            log.log(Level.SEVERE, e.getMessage(), e.getStackTrace());
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error building runtime");
            log.log(Level.SEVERE, e.getMessage(), e.getStackTrace());
        }
    }

    /**
     * Load a saved runtime from database
     */
    public void loadSavedRuntimeCode() {
        this.runtimeCode = (byte[]) db.find(DBConstants.RUNTIME_CODE)
                .orElseThrow(() -> new RuntimeCodeException("No available runtime code"));
    }

    /**
     * Updates the Host's state with information from a neighbour message.
     * Tries to update Host's set data (id and authorities) if neighbour has a greater set id than the Host.
     * Synchronized to avoid race condition between checking and updating set id
     *
     * @param neighbourMessage received neighbour message
     * @param peerId           sender of message
     */
    public void syncNeighbourMessage(NeighbourMessage neighbourMessage, PeerId peerId) {
        messageCoordinator.sendNeighbourMessageToPeer(peerId);
        if (warpSyncFinished && neighbourMessage.getSetId().compareTo(grandpaSetState.getSetId()) > 0) {
            updateSetData(neighbourMessage.getLastFinalizedBlock().add(BigInteger.ONE));
        }
    }

    private void updateSetData(BigInteger setChangeBlock) {

        List<BlockData> response = requester.requestBlockData(
                BlockRequestField.ALL,
                setChangeBlock.intValueExact(),
                1
        ).join();

        BlockData block = response.getFirst();

        if (block.getIsEmptyJustification()) {
            log.log(Level.WARNING, "No justification for block " + setChangeBlock);
            return;
        }

        Justification justification = new JustificationReader().read(
                new ScaleCodecReader(block.getJustification().toByteArray()));

        boolean verified = justification != null
                && JustificationVerifier.verify(justification.getPreCommits(), justification.getRound());

        if (verified) {
            BlockHeader header = new BlockHeaderReader().read(new ScaleCodecReader(block.getHeader().toByteArray()));

            syncState.finalizeHeader(header);

            DigestHelper.getGrandpaConsensusMessage(header.getDigest())
                    .ifPresent(cm -> grandpaSetState.handleGrandpaConsensusMessage(cm, header.getBlockNumber()));

            handleScheduledEvents();
        }
    }

    /**
     * Executes scheduled or forced authority changes for the last finalized block.
     */
    public void handleScheduledEvents() {
        boolean updated = grandpaSetState.handleAuthoritySetChange(syncState.getLastFinalizedBlockNumber());

        if (warpSyncFinished && updated) {
            new Thread(messageCoordinator::sendMessagesToPeers).start();
        }
    }
}