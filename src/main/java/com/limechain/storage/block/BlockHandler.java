package com.limechain.storage.block;

import com.limechain.exception.storage.BlockStorageGenericException;
import com.limechain.network.protocol.sync.BlockRequestField;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.request.ProtocolRequester;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.builder.RuntimeBuilder;
import lombok.extern.java.Log;
import org.javatuples.Pair;
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

    public BlockHandler(ProtocolRequester requester, RuntimeBuilder builder) {
        this.requester = requester;
        this.builder = builder;

        this.blockState = BlockState.getInstance();
    }

    public void processPendingBlocksFromQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Pair<Instant, BlockHeader> currentPair = blockState.getPendingBlocksQueue().take();
                BlockHeader header = currentPair.getValue1();
                Instant arrivalTime = currentPair.getValue0();

                log.fine("Processing announced block: "
                        + header.getBlockNumber() + " " + header.getHash());

                if (blockState.getHighestFinalizedNumber().compareTo(header.getBlockNumber()) >= 0 ||
                        blockState.hasHeader(header.getHash())) {
                    log.fine("Skipping announced block: " + header.getBlockNumber() + " " + header.getHash());
                    continue;
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

                newRuntime.executeBlock(blockWithBody);
                try {
                    blockState.addBlockWithArrivalTime(blockWithBody, arrivalTime);
                    blockState.storeRuntime(blockWithBody.getHeader().getHash(), newRuntime);
                } catch (BlockStorageGenericException ex) {
                    log.fine(String.format("[%s] %s", blockWithBody.getHeader().getHash().toString(), ex.getMessage()));
                }

                log.info("Imported announced block: " + header.getBlockNumber() + header.getHash());
            } catch (Exception e) {
                log.warning("Block announce processing malfunctioned: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }
}
