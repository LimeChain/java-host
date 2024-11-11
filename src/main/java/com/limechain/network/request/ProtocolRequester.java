package com.limechain.network.request;

import com.google.protobuf.ByteString;
import com.limechain.exception.global.ExecutionFailedException;
import com.limechain.network.Network;
import com.limechain.network.protocol.lightclient.pb.LightClientMessage;
import com.limechain.network.protocol.sync.BlockRequestDto;
import com.limechain.network.protocol.sync.BlockRequestField;
import com.limechain.network.protocol.sync.pb.SyncMessage;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockBody;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.WarpSyncResponse;
import com.limechain.network.protocol.warp.scale.reader.BlockHeaderReader;
import com.limechain.transaction.dto.Extrinsic;
import com.limechain.utils.async.AsyncExecutor;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Log
@Component
public class ProtocolRequester {

    private static final String BLOCK_REQUEST_ERROR = "There was an issue in the block request: ";

    private final AsyncExecutor asyncExecutor;
    private final Network network;

    public ProtocolRequester(Network network) {
        this.network = network;

        asyncExecutor = AsyncExecutor.withPoolSize(50);
    }

    //<editor-fold desc="Sync requests">

    public CompletableFuture<List<SyncMessage.BlockData>> requestBlockData(BlockRequestField field,
                                                                           int startNumber,
                                                                           int amount) {
        return asyncExecutor.executeAsync(() -> requestBlocks(field, startNumber, null, amount))
                .exceptionally(e -> {
                    log.fine(BLOCK_REQUEST_ERROR + e.getMessage());
                    throw new ExecutionFailedException(e);
                });
    }

    public CompletableFuture<List<SyncMessage.BlockData>> requestBlockData(BlockRequestField field,
                                                                           Hash256 startHash,
                                                                           int amount) {
        return asyncExecutor.executeAsync(() -> requestBlocks(field, null, startHash, amount))
                .exceptionally(e -> {
                    log.fine(BLOCK_REQUEST_ERROR + e.getMessage());
                    throw new ExecutionFailedException(e);
                });
    }

    public CompletableFuture<List<Block>> requestBlocks(BlockRequestField field, int startNumber, int amount) {
        return asyncExecutor.executeAsync(() -> requestBlocks(field, startNumber, null, amount).stream()
                        .map(ProtocolRequester::protobufDecodeBlock)
                        .toList())
                .exceptionally(e -> {
                    log.fine(BLOCK_REQUEST_ERROR + e.getMessage());
                    throw new ExecutionFailedException(e);
                });
    }

    public CompletableFuture<List<Block>> requestBlocks(BlockRequestField field, Hash256 startHash, int amount) {
        return asyncExecutor.executeAsync(() -> requestBlocks(field, null, startHash, amount).stream()
                        .map(ProtocolRequester::protobufDecodeBlock)
                        .toList())
                .exceptionally(e -> {
                    log.fine(BLOCK_REQUEST_ERROR + e.getMessage());
                    throw new ExecutionFailedException(e);
                });
    }

    /**
     * @param field       fields to request.
     * @param startNumber The block number to start fetching from.
     * @param startHash   The block hash to start fetching from.
     * @param amount      The number of blocks to fetch.
     * @return A list of BlockData received from the network.
     */
    private List<SyncMessage.BlockData> requestBlocks(BlockRequestField field,
                                                      Integer startNumber,
                                                      Hash256 startHash,
                                                      int amount) {
        try {
            BlockRequestDto request = new BlockRequestDto(
                    field.getValue(),
                    startHash,
                    startNumber,
                    SyncMessage.Direction.Ascending,
                    amount
            );

            SyncMessage.BlockResponse response = network.getSyncService().getProtocol().remoteBlockRequest(
                    network.getHost(),
                    network.getCurrentSelectedPeer(),
                    request);

            return response.getBlocksList();
        } catch (Exception ex) {
            log.fine("Error while fetching blocks, trying to fetch again");
            if (!this.network.updateCurrentSelectedPeerWithNextBootnode()) {
                this.network.updateCurrentSelectedPeer();
            }
            return requestBlocks(field, startNumber, startHash, amount);
        }
    }

    private static Block protobufDecodeBlock(SyncMessage.BlockData blockData) {
        // Decode the block header
        var encodedHeader = blockData.getHeader().toByteArray();
        BlockHeader blockHeader = ScaleUtils.Decode.decode(encodedHeader, new BlockHeaderReader());

        // Protobuf decode the block body
        List<Extrinsic> extrinsicsList = blockData.getBodyList().stream()
                .map(bs -> ScaleUtils.Decode.decode(bs.toByteArray(), ScaleCodecReader::readByteArray))
                .map(Extrinsic::new)
                .toList();

        BlockBody blockBody = new BlockBody(extrinsicsList);

        return new Block(blockHeader, blockBody);
    }

    //</editor-fold>

    //<editor-fold desc="State requests">

    public CompletableFuture<SyncMessage.StateResponse> requestState(String blockHash, ByteString after) {
        return asyncExecutor.executeAsync(() -> network.getStateService().getProtocol().remoteStateRequest(
                        network.getHost(),
                        network.getCurrentSelectedPeer(),
                        blockHash,
                        after
                ))
                .exceptionally(e -> {
                    log.fine("There was an issue in the state request: " + e.getMessage());
                    throw new ExecutionFailedException(e);
                });
    }

    //</editor-fold>

    //<editor-fold desc="Warp requests">

    public CompletableFuture<WarpSyncResponse> makeWarpSyncRequest(String blockHash) {
        return asyncExecutor.executeAsync(() -> network.getWarpSyncService().getProtocol().warpSyncRequest(
                        network.getHost(),
                        network.getCurrentSelectedPeer(),
                        blockHash
                ))
                .exceptionally(e -> {
                    log.fine("There was an issue in the warp request: " + e.getMessage());
                    throw new ExecutionFailedException(e);
                });
    }

    //</editor-fold>

    //<editor-fold desc="Light messages requests">

    public CompletableFuture<LightClientMessage.Response> makeRemoteReadRequest(String blockHash, String[] keys) {
        return asyncExecutor.executeAsync(() -> network.getLightMessagesService().getProtocol().remoteReadRequest(
                        network.getHost(),
                        network.getCurrentSelectedPeer(),
                        blockHash,
                        keys
                ))
                .exceptionally(e -> {
                    log.fine("There was an issue in the remote read request: " + e.getMessage());
                    throw new ExecutionFailedException(e);
                });

    }

    //</editor-fold>
}

