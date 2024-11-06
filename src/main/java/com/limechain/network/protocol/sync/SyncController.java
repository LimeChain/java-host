package com.limechain.network.protocol.sync;

import com.google.protobuf.ByteString;
import com.limechain.exception.NotImplementedException;
import com.limechain.network.protocol.sync.pb.SyncMessage;
import com.limechain.utils.LittleEndianUtils;
import io.emeraldpay.polkaj.types.Hash256;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.isNull;

public interface SyncController {
    default CompletableFuture<SyncMessage.BlockResponse> send(SyncMessage.BlockRequest req) {
        throw new NotImplementedException("Method not implemented!");
    }

    default CompletableFuture<SyncMessage.BlockResponse> sendBlockRequest(Integer fields,
                                                                          Hash256 fromHash,
                                                                          Integer fromNumber,
                                                                          SyncMessage.Direction direction,
                                                                          int maxBlocks) {
        fields = ByteBuffer.wrap(LittleEndianUtils.intTo32LEBytes(fields)).getInt();
        SyncMessage.BlockRequest.Builder syncMessage = SyncMessage.BlockRequest.newBuilder()
                .setFields(fields)
                .setDirection(direction)
                .setMaxBlocks(maxBlocks);
        if (!isNull(fromHash))
            syncMessage = syncMessage.setHash(
                    ByteString.copyFrom(LittleEndianUtils.convertBytes(fromHash.getBytes())));
        if (!isNull(fromNumber))
            syncMessage = syncMessage.setNumber(
                    ByteString.copyFrom(LittleEndianUtils.intTo32LEBytes(fromNumber)));

        var builtSyncMessage = syncMessage.build();
        return send(builtSyncMessage);
    }
}
