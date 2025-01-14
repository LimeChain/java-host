package com.limechain.storage.block;

import com.limechain.babe.BlockProductionVerifier;
import com.limechain.babe.state.EpochState;
import com.limechain.grandpa.state.RoundState;
import com.limechain.network.PeerMessageCoordinator;
import com.limechain.network.PeerRequester;
import com.limechain.network.protocol.message.ProtocolMessageBuilder;
import com.limechain.network.protocol.sync.BlockRequestField;
import com.limechain.network.protocol.warp.DigestHelper;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.RuntimeBuilder;
import com.limechain.transaction.TransactionProcessor;
import com.limechain.utils.async.AsyncExecutor;
import io.libp2p.core.PeerId;
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
    private final RoundState roundState;

    private final PeerRequester requester;
    private final PeerMessageCoordinator messageCoordinator;

    private final RuntimeBuilder builder;
    private final AsyncExecutor asyncExecutor;
    private final TransactionProcessor transactionProcessor;
    private final BlockProductionVerifier verifier;

    public BlockHandler(EpochState epochState,
                        PeerRequester requester,
                        RuntimeBuilder builder,
                        TransactionProcessor transactionProcessor,
                        PeerMessageCoordinator messageCoordinator,
                        RoundState roundState) {

        this.epochState = epochState;
        this.requester = requester;
        this.messageCoordinator = messageCoordinator;
        this.builder = builder;
        this.transactionProcessor = transactionProcessor;
        this.verifier = new BlockProductionVerifier();
        blockState = BlockState.getInstance();
        asyncExecutor = AsyncExecutor.withPoolSize(10);
        this.roundState = roundState;
    }

    public synchronized void handleBlockHeader(Instant arrivalTime, BlockHeader header, PeerId excluding) {
        try {
            Runtime runtime = blockState.getRuntime(header.getParentHash());
            Runtime newRuntime = builder.copyRuntime(runtime);

            if (epochState.isInitialized() && !verifier.isAuthorshipValid(newRuntime,
                    header,
                    epochState.getCurrentEpochData(),
                    epochState.getCurrentEpochDescriptor(),
                    epochState.getCurrentEpochIndex())) {
                return;
            }

            if (blockState.hasHeader(header.getHash())) {
                log.fine("Skipping announced block: " + header.getBlockNumber() + " " + header.getHash());
                return;
            }

            CompletableFuture<List<Block>> responseFuture = requester.requestBlocks(
                    BlockRequestField.ALL, header.getHash(), 1);

            List<Block> blocks = responseFuture.join();
            while (blocks.isEmpty()) {
                blocks = requester.requestBlocks(
                        BlockRequestField.ALL, header.getHash(), 1).join();
            }

            Block block = blocks.getFirst();

            newRuntime.executeBlock(block);
            log.fine(String.format("Executed block No: %s with hash: %s.",
                    block.getHeader().getBlockNumber(), header.getHash()));
            blockState.storeRuntime(header.getHash(), runtime);

            handleBlock(block, arrivalTime);

            messageCoordinator.sendBlockAnnounceMessageExcludingPeer(
                    ProtocolMessageBuilder.buildBlockAnnounceMessage(
                            block.getHeader(), block.getHeader().getHash().equals(blockState.bestBlockHash())),
                    excluding);
        } catch (Exception e) {
            log.warning("Error while importing announced block: " + e);
        }
    }

    public void handleProducedBlock(Block block) {
        handleBlock(block, Instant.now());
        messageCoordinator.sendBlockAnnounceMessageExcludingPeer(
                ProtocolMessageBuilder.buildBlockAnnounceMessage(block.getHeader(), true),
                null);
    }

    private void handleBlock(Block block, Instant arrivalTime) {
        BlockHeader header = block.getHeader();

        blockState.addBlockWithArrivalTime(block, arrivalTime);
        log.fine(String.format("Added block No: %s with hash: %s to block tree.",
                block.getHeader().getBlockNumber(), header.getHash()));

        DigestHelper.getBabeConsensusMessage(header.getDigest())
                .ifPresent(cm -> {
                    epochState.updateNextEpochConfig(cm);
                    log.fine(String.format("Updated epoch block config: %s", cm.getFormat().toString()));
                });

        DigestHelper.getGrandpaConsensusMessage(header.getDigest())
                .ifPresent(cm -> {
                    roundState.handleGrandpaConsensusMessage(cm);
                    log.fine(String.format("Updated grandpa set config: %s", cm.getFormat().toString()));
                });

        asyncExecutor.executeAndForget(() -> transactionProcessor.maintainTransactionPool(block));
    }
}