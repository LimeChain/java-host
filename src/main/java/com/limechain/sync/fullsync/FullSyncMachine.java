package com.limechain.sync.fullsync;

import com.google.protobuf.ByteString;
import com.limechain.config.HostConfig;
import com.limechain.exception.storage.BlockNodeNotFoundException;
import com.limechain.exception.sync.BlockExecutionException;
import com.limechain.network.Network;
import com.limechain.network.protocol.blockannounce.NodeRole;
import com.limechain.network.protocol.sync.BlockRequestDto;
import com.limechain.network.protocol.sync.pb.SyncMessage;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockBody;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.Extrinsics;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.network.protocol.warp.scale.reader.BlockHeaderReader;
import com.limechain.rpc.server.AppBean;
import com.limechain.runtime.Runtime;
import com.limechain.babe.state.EpochState;
import com.limechain.runtime.builder.RuntimeBuilder;
import com.limechain.runtime.version.StateVersion;
import com.limechain.storage.block.BlockState;
import com.limechain.storage.block.SyncState;
import com.limechain.storage.trie.TrieStorage;
import com.limechain.sync.fullsync.inherents.InherentData;
import com.limechain.trie.DiskTrieAccessor;
import com.limechain.trie.TrieAccessor;
import com.limechain.trie.TrieStructureFactory;
import com.limechain.trie.structure.TrieStructure;
import com.limechain.trie.structure.database.NodeData;
import com.limechain.trie.structure.nibble.Nibbles;
import com.limechain.utils.scale.ScaleUtils;
import com.limechain.utils.scale.readers.PairReader;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.reader.ListReader;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.Getter;
import lombok.extern.java.Log;
import org.apache.commons.lang3.ArrayUtils;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FullSyncMachine is responsible for executing full synchronization of blocks.
 * It communicates with the network to fetch blocks, execute them, and update the trie structure accordingly.
 */
@Getter
@Log
public class FullSyncMachine {

    private final HostConfig hostConfig;
    private final Network networkService;
    private final SyncState syncState;
    private final BlockState blockState = BlockState.getInstance();
    private final TrieStorage trieStorage = AppBean.getBean(TrieStorage.class);
    private final RuntimeBuilder runtimeBuilder = AppBean.getBean(RuntimeBuilder.class);
    private final EpochState epochState = AppBean.getBean(EpochState.class);
    private Runtime runtime = null;

    public FullSyncMachine(HostConfig hostConfig, Network networkService, SyncState syncState) {
        this.hostConfig = hostConfig;
        this.networkService = networkService;
        this.syncState = syncState;
    }

    public void start() {
        // TODO: DIRTY INITIALIZATION FIX:
        //  this.networkService.currentSelectedPeer is null,
        //  unless explicitly set via some of the "update..." methods
        this.networkService.updateCurrentSelectedPeerWithNextBootnode();

        Hash256 stateRoot = syncState.getStateRoot();
        Hash256 lastFinalizedBlockHash = syncState.getLastFinalizedBlockHash();

        DiskTrieAccessor trieAccessor = new DiskTrieAccessor(trieStorage, stateRoot.getBytes());

        if (!trieStorage.merkleValueExists(stateRoot)) {
            loadStateAtBlockFromPeer(lastFinalizedBlockHash);
        }

        runtime = buildRuntimeFromState(trieAccessor);
        StateVersion runtimeStateVersion = runtime.getVersion().getStateVersion();
        trieAccessor.setCurrentStateVersion(runtimeStateVersion);

        byte[] calculatedMerkleRoot = trieAccessor.getMerkleRoot(runtimeStateVersion);
        if (!stateRoot.equals(new Hash256(calculatedMerkleRoot))) {
            log.info("State root is not equal to the one in the trie, cannot start full sync");
            return;
        }

        blockState.storeRuntime(lastFinalizedBlockHash, runtime);

        int startNumber = syncState.getLastFinalizedBlockNumber()
                .add(BigInteger.ONE)
                .intValue();

        int blocksToFetch = 100;
        List<Block> receivedBlocks = requestBlocks(startNumber, blocksToFetch);

        while (!receivedBlocks.isEmpty()) {
            executeBlocks(receivedBlocks, trieAccessor);
            log.info("Executed blocks from " + receivedBlocks.getFirst().getHeader().getBlockNumber()
                    + " to " + receivedBlocks.getLast().getHeader().getBlockNumber());
            startNumber += blocksToFetch;
            receivedBlocks = requestBlocks(startNumber, blocksToFetch);
        }

        if (NodeRole.AUTHORING.equals(hostConfig.getNodeRole())) {
            initializeEpochState();
        }

        blockState.setFullSyncFinished(true);
    }

    private void initializeEpochState() {
        epochState.initialize(runtime.callBabeApiConfiguration());
        epochState.setGenesisSlotNumber(runtime.retrieveGenesisSlot());
    }

    private TrieStructure<NodeData> loadStateAtBlockFromPeer(Hash256 lastFinalizedBlockHash) {
        log.info("Loading state at block from peer");
        Map<ByteString, ByteString> kvps = makeStateRequest(lastFinalizedBlockHash);

        TrieStructure<NodeData> trieStructure = TrieStructureFactory.buildFromKVPs(kvps);
        trieStorage.insertTrieStorage(trieStructure);
        log.info("State at block loaded from peer");

        kvps.clear();
        return trieStructure;
    }

    private Map<ByteString, ByteString> makeStateRequest(Hash256 lastFinalizedBlockHash) {
        Map<ByteString, ByteString> kvps = new HashMap<>();

        ByteString start = ByteString.EMPTY;

        while (true) {
            final SyncMessage.StateResponse response;
            try {
                response = networkService.makeStateRequest(lastFinalizedBlockHash.toString(), start);
            } catch (Exception ex) {
                if (!this.networkService.updateCurrentSelectedPeerWithNextBootnode()) {
                    this.networkService.updateCurrentSelectedPeer();
                }
                continue;
            }

            for (SyncMessage.KeyValueStateEntry keyValueStateEntry : response.getEntriesList()) {
                for (SyncMessage.StateEntry stateEntry : keyValueStateEntry.getEntriesList()) {
                    kvps.put(stateEntry.getKey(), stateEntry.getValue());
                }
            }

            SyncMessage.KeyValueStateEntry lastEntry = response.getEntriesList().getLast();
            if (!lastEntry.getComplete()) {
                start = lastEntry.getEntriesList().getLast().getKey();
            } else {
                break;
            }
        }

        return kvps;
    }

    /**
     * Fetches blocks from the network.
     *
     * @param start  The block number to start fetching from.
     * @param amount The number of blocks to fetch.
     * @return A list of BlockData received from the network.
     */
    private List<Block> requestBlocks(int start, int amount) {
        try {
            final int HEADER = 0b0000_0001;
            final int BODY = 0b0000_0010;
            final int JUSTIFICATION = 0b0001_0000;
            SyncMessage.BlockResponse response = networkService.makeBlockRequest(new BlockRequestDto(
                    HEADER | BODY | JUSTIFICATION,
                    null, // no hash, number instead
                    start,
                    SyncMessage.Direction.Ascending,
                    amount
            ));

            List<SyncMessage.BlockData> blockDatas = response.getBlocksList();

            return blockDatas.stream()
                    .map(FullSyncMachine::protobufDecodeBlock)
                    .toList();
        } catch (Exception ex) {
            log.info("Error while fetching blocks, trying to fetch again");
            if (!this.networkService.updateCurrentSelectedPeerWithNextBootnode()) {
                this.networkService.updateCurrentSelectedPeer();
            }
            return requestBlocks(start, amount);
        }
    }

    // TODO: This method doesn't belong in this class. Move when appropriate.
    private static Block protobufDecodeBlock(SyncMessage.BlockData blockData) {
        // Decode the block header
        var encodedHeader = blockData.getHeader().toByteArray();
        BlockHeader blockHeader = ScaleUtils.Decode.decode(encodedHeader, new BlockHeaderReader());

        // Protobuf decode the block body
        List<Extrinsics> extrinsicsList = blockData.getBodyList().stream()
                .map(bs -> ScaleUtils.Decode.decode(bs.toByteArray(), ScaleCodecReader::readByteArray))
                .map(Extrinsics::new)
                .toList();

        BlockBody blockBody = new BlockBody(extrinsicsList);

        return new Block(blockHeader, blockBody);
    }

    /**
     * Executes blocks received from the network.
     *
     * @param receivedBlockDatas A list of BlockData to execute.
     */
    private void executeBlocks(List<Block> receivedBlockDatas, TrieAccessor trieAccessor) {
        for (Block block : receivedBlockDatas) {
            log.fine("Block number to be executed is " + block.getHeader().getBlockNumber());

            try {
                blockState.addBlock(block);
            } catch (BlockNodeNotFoundException ex) {
                log.fine("Executing block with number " + block.getHeader().getBlockNumber() + " which has no parent in block state.");
            }

            // Check the block for valid inherents
            // NOTE: This is only relevant for block production.
            //  We will need this functionality in near future,
            //  but we don't need it when importing blocks for the full sync.
            boolean goodToExecute = this.checkInherents(block);

            log.fine("Block is good to execute: " + goodToExecute);

            if (!goodToExecute) {
                log.fine("Block not executed");
                throw new BlockExecutionException();
            }

            // Actually execute the block and persist changes
            runtime.executeBlock(block);
            log.fine("Block executed successfully");

            // Persist the updates to the trie structure
            trieAccessor.persistChanges();

            BlockHeader blockHeader = block.getHeader();
            try {
                blockState.setFinalizedHash(blockHeader.getHash(), BigInteger.ZERO, BigInteger.ZERO);
            } catch (BlockNodeNotFoundException ignored) {
                log.fine("Executing block with number " + block.getHeader().getBlockNumber() + " which has no parent in block state.");
            }

            boolean blockUpdatedRuntime = Arrays.stream(blockHeader.getDigest())
                    .map(HeaderDigest::getType)
                    .anyMatch(type -> type.equals(DigestType.RUN_ENV_UPDATED));

            if (blockUpdatedRuntime) {
                log.info("Runtime updated, updating the runtime code");
                runtime = buildRuntimeFromState(trieAccessor);
                trieAccessor.setCurrentStateVersion(runtime.getVersion().getStateVersion());
                blockState.storeRuntime(blockHeader.getHash(), runtime);
            }
        }
    }

    private Runtime buildRuntimeFromState(TrieAccessor trieAccessor) {
        return trieAccessor
                .findStorageValue(Nibbles.fromBytes(":code".getBytes()))
                .map(wasm -> runtimeBuilder.buildRuntime(wasm, trieAccessor))
                .orElseThrow(() -> new RuntimeException("Runtime code not found in the trie"));
    }

    private boolean checkInherents(Block block) {
        // Call BlockBuilder_check_inherents to check the inherents of the block
        InherentData inherents = new InherentData(System.currentTimeMillis());
        byte[] checkInherentsOutput = runtime.checkInherents(block, inherents);

        // Check if the block is good to execute based on the output of BlockBuilder_check_inherents
        return isBlockGoodToExecute(checkInherentsOutput);
    }

    /**
     * Checks whether a block is good to execute based on the output of BlockBuilder_check_inherents.
     *
     * @param checkInherentsOutput The output of BlockBuilder_check_inherents.
     * @return True if the block is good to execute, false otherwise.
     */
    private static boolean isBlockGoodToExecute(byte[] checkInherentsOutput) {
        var data = ScaleUtils.Decode.decode(
                ArrayUtils.subarray(checkInherentsOutput, 2, checkInherentsOutput.length),
                new ListReader<>(
                        new PairReader<>(
                                scr -> new String(scr.readByteArray(8)),
                                scr -> new String(scr.readByteArray())
                        )
                )
        );

        boolean goodToExecute;

        if (data.size() > 1) {
            goodToExecute = false;
        } else if (data.size() == 1) {
            //If the inherent is babeslot or auraslot, then it's an expected issue and we can proceed
            goodToExecute = data.get(0).getValue0().equals("babeslot") || data.get(0).getValue0().equals("auraslot");
        } else {
            goodToExecute = true;
        }
        return goodToExecute;
    }
}
