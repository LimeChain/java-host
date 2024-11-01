package com.limechain.sync.warpsync;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.exception.global.RuntimeCodeException;
import com.limechain.exception.trie.TrieDecoderException;
import com.limechain.network.Network;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import com.limechain.network.protocol.grandpa.messages.neighbour.NeighbourMessage;
import com.limechain.network.protocol.lightclient.pb.LightClientMessage;
import com.limechain.network.protocol.sync.BlockRequestField;
import com.limechain.network.protocol.sync.pb.SyncMessage.BlockData;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.ConsensusEngine;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.network.protocol.warp.dto.Justification;
import com.limechain.network.protocol.warp.scale.reader.BlockHeaderReader;
import com.limechain.network.protocol.warp.scale.reader.JustificationReader;
import com.limechain.network.request.ProtocolRequester;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.builder.RuntimeBuilder;
import com.limechain.storage.DBConstants;
import com.limechain.storage.KVRepository;
import com.limechain.storage.block.SyncState;
import com.limechain.sync.JustificationVerifier;
import com.limechain.sync.warpsync.dto.AuthoritySetChange;
import com.limechain.sync.warpsync.dto.GrandpaDigestMessageType;
import com.limechain.sync.warpsync.scale.ForcedChangeReader;
import com.limechain.sync.warpsync.scale.ScheduledChangeReader;
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
import org.javatuples.Pair;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.logging.Level;

/**
 * Singleton class, holds and handles the synced state of the Host.
 */
@Log
@Setter
public class WarpSyncState {

    private final SyncState syncState;
    private final Network network;
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
    private final ProtocolRequester requester;
    //TODO Yordan: maybe we won't need this anymore.
    private final Set<BigInteger> scheduledRuntimeUpdateBlocks;
    private final PriorityQueue<Pair<BigInteger, Authority[]>> scheduledAuthorityChanges;


    public WarpSyncState(SyncState syncState,
                         Network network,
                         KVRepository<String, Object> db,
                         RuntimeBuilder runtimeBuilder,
                         ProtocolRequester requester) {
        this(syncState,
                network,
                db,
                runtimeBuilder,
                new HashSet<>(),
                new PriorityQueue<>(Comparator.comparing(Pair::getValue0)),
                requester);
    }

    public WarpSyncState(SyncState syncState,
                         Network network,
                         KVRepository<String, Object> db,
                         RuntimeBuilder runtimeBuilder, Set<BigInteger> scheduledRuntimeUpdateBlocks,
                         PriorityQueue<Pair<BigInteger, Authority[]>> scheduledAuthorityChanges,
                         ProtocolRequester requester) {
        this.syncState = syncState;
        this.network = network;
        this.db = db;
        this.runtimeBuilder = runtimeBuilder;
        this.scheduledRuntimeUpdateBlocks = scheduledRuntimeUpdateBlocks;
        this.scheduledAuthorityChanges = scheduledAuthorityChanges;
        this.requester = requester;
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
                + " with " + commitMessage.getPrecommits().length + " voters");

        boolean verified = JustificationVerifier.verify(commitMessage.getPrecommits(), commitMessage.getRoundNumber());
        if (!verified) {
            log.log(Level.WARNING, "Could not verify commit from peer: " + peerId);
            return;
        }

        if (warpSyncFinished) {
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
        network.sendNeighbourMessage(peerId);
        if (warpSyncFinished && neighbourMessage.getSetId().compareTo(syncState.getSetId()) > 0) {
            updateSetData(neighbourMessage.getLastFinalizedBlock().add(BigInteger.ONE));
        }
    }

    private void updateSetData(BigInteger setChangeBlock) {
        List<BlockData> response = requester.requestBlockData(
                BlockRequestField.ALL, setChangeBlock.intValue(), 1).join();
        BlockData block = response.getFirst();

        if (block.getIsEmptyJustification()) {
            log.log(Level.WARNING, "No justification for block " + setChangeBlock);
            return;
        }

        Justification justification = new JustificationReader().read(
                new ScaleCodecReader(block.getJustification().toByteArray()));
        boolean verified = justification != null
                && JustificationVerifier.verify(justification.getPrecommits(), justification.getRound());

        if (verified) {
            BlockHeader header = new BlockHeaderReader().read(new ScaleCodecReader(block.getHeader().toByteArray()));

            syncState.finalizeHeader(header);
            handleAuthorityChanges(header.getDigest(), setChangeBlock);
            handleScheduledEvents();
        }
    }

    /**
     * Executes authority changes, scheduled for the current block.
     */
    public void handleScheduledEvents() {
        Pair<BigInteger, Authority[]> data = scheduledAuthorityChanges.peek();
        BigInteger setId = syncState.getSetId();
        boolean updated = false;
        while (data != null) {
            if (data.getValue0().compareTo(syncState.getLastFinalizedBlockNumber()) < 1) {
                setId = syncState.incrementSetId();
                syncState.resetRound();
                syncState.setAuthoritySet(data.getValue1());
                scheduledAuthorityChanges.poll();
                updated = true;
            } else break;
            data = scheduledAuthorityChanges.peek();
        }
        if (warpSyncFinished && updated) {
            log.log(Level.INFO, "Successfully transitioned to authority set id: " + setId);
            new Thread(network::sendMessagesToPeers).start();
        }
    }

    /**
     * Handles authority changes coming from a block header digest and schedules them.
     *
     * @param headerDigests digest of the block header
     * @param blockNumber   block that contains the digest
     */
    public void handleAuthorityChanges(HeaderDigest[] headerDigests, BigInteger blockNumber) {
        // Update authority set and set id
        AuthoritySetChange authorityChanges;
        for (HeaderDigest digest : headerDigests) {
            if (digest.getId() == ConsensusEngine.GRANDPA) {
                ScaleCodecReader reader = new ScaleCodecReader(digest.getMessage());
                GrandpaDigestMessageType type = GrandpaDigestMessageType.fromId(reader.readByte());

                if (type == null) {
                    log.log(Level.SEVERE, "Could not get grandpa message type");
                    throw new IllegalStateException("Unknown grandpa message type");
                }

                switch (type) {
                    case SCHEDULED_CHANGE -> {
                        ScheduledChangeReader authorityChangesReader = new ScheduledChangeReader();
                        authorityChanges = authorityChangesReader.read(reader);
                        scheduledAuthorityChanges
                                .add(new Pair<>(blockNumber.add(authorityChanges.getDelay()),
                                        authorityChanges.getAuthorities()));
                        return;
                    }
                    case FORCED_CHANGE -> {
                        ForcedChangeReader authorityForcedChangesReader = new ForcedChangeReader();
                        authorityChanges = authorityForcedChangesReader.read(reader);
                        scheduledAuthorityChanges
                                .add(new Pair<>(blockNumber.add(authorityChanges.getDelay()),
                                        authorityChanges.getAuthorities()));
                        return;
                    }
                    case ON_DISABLED -> {
                        log.log(Level.SEVERE, "'ON DISABLED' grandpa message not implemented");
                        return;
                    }
                    case PAUSE -> {
                        log.log(Level.SEVERE, "'PAUSE' grandpa message not implemented");
                        return;
                    }
                    case RESUME -> {
                        log.log(Level.SEVERE, "'RESUME' grandpa message not implemented");
                        return;
                    }
                }
            }
        }
    }

}