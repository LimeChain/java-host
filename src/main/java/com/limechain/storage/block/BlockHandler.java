package com.limechain.storage.block;

import com.limechain.babe.state.EpochState;
import com.limechain.network.protocol.sync.BlockRequestField;
import com.limechain.network.protocol.warp.DigestHelper;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.PeerRequester;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.RuntimeBuilder;
import com.limechain.transaction.TransactionProcessor;
import com.limechain.utils.async.AsyncExecutor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@Log
public class BlockHandler {

    private final BlockState blockState;
    private final EpochState epochState;
    private final PeerRequester requester;
    private final RuntimeBuilder builder;
    private final TransactionProcessor transactionProcessor;

    private final AsyncExecutor asyncExecutor;

    public BlockHandler(EpochState epochState,
                        PeerRequester requester,
                        RuntimeBuilder builder,
                        TransactionProcessor transactionProcessor) {
        this.epochState = epochState;
        this.requester = requester;
        this.transactionProcessor = transactionProcessor;
        this.builder = builder;

        this.blockState = BlockState.getInstance();
        asyncExecutor = AsyncExecutor.withPoolSize(10);
    }

    public synchronized void handleBlockHeader(Instant arrivalTime, BlockHeader header) {
        try {
            if (blockState.hasHeader(header.getHash())) {
                log.fine("Skipping announced block: " + header.getBlockNumber() + " " + header.getHash());
                return;
            }

            CompletableFuture<List<Block>> responseFuture = requester.requestBlocks(
                    BlockRequestField.ALL, header.getHash(), 1);

            Runtime runtime = blockState.getRuntime(header.getParentHash());
            Runtime newRuntime = builder.copyRuntime(runtime);

            List<Block> blocks = responseFuture.join();
            while (blocks.isEmpty()) {
                blocks = requester.requestBlocks(
                        BlockRequestField.ALL, header.getHash(), 1).join();
            }

            handleBlock(newRuntime, blocks.getFirst(), arrivalTime);
        } catch (Exception e) {
            log.warning("Error while importing announced block: " + e.getMessage());
        }
    }

    private void handleBlock(Runtime runtime, Block block, Instant arrivalTime) {
        BlockHeader header = block.getHeader();

        blockState.addBlockWithArrivalTime(block, arrivalTime);
        blockState.storeRuntime(header.getHash(), runtime);
        log.fine(String.format("Added block No: %s with hash: %s to block tree.",
                block.getHeader().getBlockNumber(), header.getHash()));

        runtime.executeBlock(block);
        log.fine(String.format("Executed block No: %s with hash: %s.",
                block.getHeader().getBlockNumber(), header.getHash()));

        DigestHelper.getBabeConsensusMessage(header.getDigest())
                .ifPresent(cm -> {
                    epochState.updateNextEpochConfig(cm);
                    log.fine(String.format("Updated epoch block config: %s", cm.getFormat().toString()));
                });

        asyncExecutor.executeAndForget(() -> transactionProcessor.maintainTransactionPool(block));
    }
}