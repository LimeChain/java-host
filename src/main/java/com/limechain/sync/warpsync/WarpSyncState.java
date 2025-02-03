package com.limechain.sync.warpsync;

import com.limechain.exception.global.RuntimeCodeException;
import com.limechain.exception.trie.TrieDecoderException;
import com.limechain.network.PeerRequester;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.lightclient.pb.LightClientMessage;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.RuntimeBuilder;
import com.limechain.state.StateManager;
import com.limechain.storage.DBConstants;
import com.limechain.storage.KVRepository;
import com.limechain.sync.state.SyncState;
import com.limechain.trie.decoded.Trie;
import com.limechain.trie.decoded.TrieVerifier;
import com.limechain.utils.LittleEndianUtils;
import com.limechain.utils.StringUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * Singleton class, holds and handles the synced state of the Host.
 */
@Log
@Setter
public class WarpSyncState {

    private final StateManager stateManager;
    private final PeerRequester requester;
    private final KVRepository<String, Object> db;

    public static final String CODE_KEY = StringUtils.toHex(":code");

    @Getter
    private boolean warpSyncFragmentsFinished;
    @Getter
    private boolean warpSyncFinished = false;

    @Getter
    private Runtime runtime;
    @Getter
    private byte[] runtimeCode;

    protected final RuntimeBuilder runtimeBuilder;
    //TODO Yordan: maybe we won't need this anymore.
    private final Set<BigInteger> scheduledRuntimeUpdateBlocks;

    public WarpSyncState(StateManager stateManager,
                         KVRepository<String, Object> db,
                         RuntimeBuilder runtimeBuilder,
                         PeerRequester requester) {

        this(stateManager,
                db,
                runtimeBuilder,
                new HashSet<>(),
                requester
        );
    }

    public WarpSyncState(StateManager stateManager,
                         KVRepository<String, Object> db,
                         RuntimeBuilder runtimeBuilder, Set<BigInteger> scheduledRuntimeUpdateBlocks,
                         PeerRequester requester) {

        this.stateManager = stateManager;
        this.db = db;
        this.runtimeBuilder = runtimeBuilder;
        this.scheduledRuntimeUpdateBlocks = scheduledRuntimeUpdateBlocks;
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

    public void updateRuntime(BigInteger blockNumber) {
        if (!scheduledRuntimeUpdateBlocks.contains(blockNumber)) {
            return;
        }
        updateRuntimeCode();
        buildRuntime();
        BigInteger lastFinalizedBlockNumber = stateManager.getSyncState().getLastFinalizedBlockNumber();
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
        SyncState syncState = stateManager.getSyncState();
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
}