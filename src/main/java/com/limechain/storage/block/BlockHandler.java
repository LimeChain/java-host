package com.limechain.storage.block;

import com.limechain.babe.BlockProductionVerifier;
import com.limechain.babe.state.EpochState;
import com.limechain.config.HostConfig;
import com.limechain.exception.storage.BlockStorageGenericException;
import com.limechain.grandpa.state.RoundState;
import com.limechain.network.PeerMessageCoordinator;
import com.limechain.network.PeerRequester;
import com.limechain.network.protocol.blockannounce.NodeRole;
import com.limechain.network.protocol.message.ProtocolMessageBuilder;
import com.limechain.network.protocol.sync.BlockRequestField;
import com.limechain.network.protocol.warp.DigestHelper;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.RuntimeBuilder;
import com.limechain.state.StateManager;
import com.limechain.state.AbstractState;
import com.limechain.storage.block.state.BlockState;
import com.limechain.sync.SyncMode;
import com.limechain.sync.state.SyncState;
import com.limechain.transaction.TransactionProcessor;
import com.limechain.utils.async.AsyncExecutor;
import io.emeraldpay.polkaj.types.Hash256;
import io.libp2p.core.PeerId;
import lombok.extern.java.Log;
import org.javatuples.Pair;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Log
@Component
public class BlockHandler {

    private final StateManager stateManager;

    private final PeerRequester requester;
    private final PeerMessageCoordinator messageCoordinator;

    private final RuntimeBuilder builder;
    private final HostConfig hostConfig;
    private final TransactionProcessor transactionProcessor;
    private final BlockProductionVerifier verifier;

    private final AsyncExecutor asyncExecutor;

    private final HashMap<Hash256, BlockHeader> blockHeaders;
    private final ArrayDeque<Pair<Instant, Block>> pendingBlocksQueue;

    public BlockHandler(StateManager stateManager,
                        RoundState roundState,
                        SyncState syncState,
                        PeerRequester requester,
                        RuntimeBuilder builder,
                        HostConfig hostConfig,
                        TransactionProcessor transactionProcessor,
                        PeerMessageCoordinator messageCoordinator) {

        this.stateManager = stateManager;

        this.requester = requester;
        this.messageCoordinator = messageCoordinator;

        this.builder = builder;
        this.hostConfig = hostConfig;
        this.transactionProcessor = transactionProcessor;
        this.verifier = new BlockProductionVerifier();

        asyncExecutor = AsyncExecutor.withPoolSize(10);
        blockHeaders = new HashMap<>();
        pendingBlocksQueue = new ArrayDeque<>();
    }

    public synchronized void handleAnnounced(BlockHeader header, Instant arrivalTime, PeerId peerId) {

        if (blockHeaders.containsKey(header.getHash()) || blockState.hasHeader(header.getHash())) {
            log.fine("Skipping announced block: " + header.getBlockNumber() + " " + header.getHash());
            return;
        }

        if (!AbstractState.getSyncMode().equals(SyncMode.HEAD)) {
            addBlockToQueue(header, arrivalTime);
            return;
        }

        processPendingBlocksFromQueue();

        Block block = requestBlock(header);
        processBlock(block, arrivalTime);

        messageCoordinator.sendBlockAnnounceMessageExcludingPeer(
                ProtocolMessageBuilder.buildBlockAnnounceMessage(
                        header, header.getHash().equals(blockState.bestBlockHash())),
                peerId);
    }

    public synchronized void handleProduced(Block block) {

        addBlockToTree(block, Instant.now());
        messageCoordinator.sendBlockAnnounceMessageExcludingPeer(
                ProtocolMessageBuilder.buildBlockAnnounceMessage(block.getHeader(), true),
                null);
    }

    private void processBlock(Block block, Instant arrivalTime) {
        addBlockToTree(block, arrivalTime);

        if (!hostConfig.getNodeRole().equals(NodeRole.LIGHT)) {
            verifyAndExecuteBlock(block);
        }
    }

    private void verifyAndExecuteBlock(Block block) {

        try {
            BlockHeader header = block.getHeader();

            Runtime runtime = blockState.getRuntime(header.getParentHash());
            Runtime newRuntime = builder.copyRuntime(runtime);

            if (!verifier.isAuthorshipValid(newRuntime,
                    header,
                    epochState.getCurrentEpochData(),
                    epochState.getCurrentEpochDescriptor(),
                    epochState.getCurrentEpochIndex())) {
                return;
            }

            newRuntime.executeBlock(block);
            log.fine(String.format("Executed block No: %s with hash: %s.",
                    block.getHeader().getBlockNumber(), header.getHash()));
            blockState.storeRuntime(header.getHash(), runtime);

            asyncExecutor.executeAndForget(() -> transactionProcessor.maintainTransactionPool(block));
        } catch (Exception e) {
            log.warning("Error while importing announced block: " + e);
        }
    }

    private void addBlockToTree(Block block, Instant arrivalTime) {

        BlockHeader header = block.getHeader();

        stateManager.getBlockState().addBlockWithArrivalTime(block, arrivalTime);
        log.fine(String.format("Added block No: %s with hash: %s to block tree.",
                block.getHeader().getBlockNumber(), header.getHash()));

        if (epochState.isInitialized()) {
            asyncExecutor.executeAndForget(() -> DigestHelper.getBabeConsensusMessage(header.getDigest())
                    .ifPresent(cm -> {
                        stateManager.getEpochState().updateNextEpochConfig(cm);
                        log.fine(String.format("Updated epoch block config: %s", cm.getFormat().toString()));
                    }));
        }

        if (roundState.isInitialized()) {
            asyncExecutor.executeAndForget(() -> DigestHelper.getGrandpaConsensusMessage(header.getDigest())
                    .ifPresent(cm ->
                            roundState.handleGrandpaConsensusMessage(cm, header.getBlockNumber())
                    ));

        roundState.handleAuthoritySetChange(header.getBlockNumber());
    }
}

private void addBlockToQueue(BlockHeader blockHeader, Instant arrivalTime) {

    blockHeaders.put(blockHeader.getHash(), blockHeader);

    asyncExecutor.executeAndForget(() -> {
        Block block = requestBlock(blockHeader);
        pendingBlocksQueue.add(Pair.with(arrivalTime, block));
        log.fine("Added block to queue " + block.getHeader().getBlockNumber() + " " + block.getHeader().getHash());
    });
}

private void processPendingBlocksFromQueue() {

    while (!pendingBlocksQueue.isEmpty()) {
        var currentPair = pendingBlocksQueue.poll();
        var block = currentPair.getValue1();
        var arrivalTime = currentPair.getValue0();

        blockHeaders.remove(block.getHeader().getHash());

        if (block.getHeader().getBlockNumber().compareTo(syncState.getLastFinalizedBlockNumber()) <= 0) {
            continue;
        }

        try {
            processBlock(block, arrivalTime);
        } catch (BlockStorageGenericException ex) {
            log.fine(String.format("[%s] %s", block.getHeader().getHash().toString(), ex.getMessage()));
        }
    }
}

private Block requestBlock(BlockHeader header) {

    List<Block> blocks = new ArrayList<>();

    while (blocks.isEmpty()) {
        CompletableFuture<List<Block>> responseFuture = requester.requestBlocks(
                BlockRequestField.ALL, header.getHash(), 1);

        blocks = responseFuture.join();
    }

    log.fine("Request successful " + blocks.getFirst().getHeader().getHash());
    return blocks.getFirst();
}
}