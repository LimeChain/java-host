package com.limechain.storage.block;

import com.limechain.exception.storage.BlockStorageGenericException;
import com.limechain.network.protocol.sync.BlockRequestField;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.request.ProtocolRequester;
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
    private final ProtocolRequester requester;
    private final RuntimeBuilder builder;
    private final TransactionProcessor transactionProcessor;

    private final AsyncExecutor asyncExecutor;

    public BlockHandler(ProtocolRequester requester,
                        RuntimeBuilder builder,
                        TransactionProcessor transactionProcessor) {

        this.requester = requester;
        this.builder = builder;
        this.transactionProcessor = transactionProcessor;

        this.blockState = BlockState.getInstance();
        asyncExecutor = AsyncExecutor.withPoolSize(10);
    }

    public synchronized void handleBlock(Instant arrivalTime, BlockHeader header) {
        try {
            if (blockState.getHighestFinalizedNumber().compareTo(header.getBlockNumber()) >= 0
                    || blockState.hasHeader(header.getHash())) {
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

            Block blockWithBody = blocks.getFirst();

            importBlock(newRuntime, blockWithBody, arrivalTime);

            asyncExecutor.executeAndForget(() -> {
                try {
                    newRuntime.executeBlock(blockWithBody);
                    log.info("Finished executing " + blockWithBody.getHeader().getHash());
                    transactionProcessor.maintainTransactionPool(blockWithBody);
                } catch (BlockStorageGenericException ex) {
                    log.warning(ex.getMessage());
                }
            });

        } catch (Exception e) {
            // TODO Think of a proper way to restart importing process.
            log.warning("Block announce processing malfunctioned: " + e.getMessage());
        }
    }

    private void importBlock(Runtime newRuntime, Block blockWithBody, Instant arrivalTime) {
        try {
            blockState.addBlockWithArrivalTime(blockWithBody, arrivalTime);
            blockState.storeRuntime(blockWithBody.getHeader().getHash(), newRuntime);
        } catch (BlockStorageGenericException ex) {
            throw new BlockStorageGenericException(ex.getMessage());
        }

        log.info("Imported announced block: " + blockWithBody.getHeader().getBlockNumber()
                + " " + blockWithBody.getHeader().getHash());
    }
}