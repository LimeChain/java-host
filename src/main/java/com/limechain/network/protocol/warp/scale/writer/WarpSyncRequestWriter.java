package com.limechain.network.protocol.warp.scale.writer;

import com.limechain.network.protocol.warp.dto.WarpSyncRequest;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WarpSyncRequestWriter implements ScaleWriter<WarpSyncRequest> {

    private static final WarpSyncRequestWriter INSTANCE = new WarpSyncRequestWriter();

    public static WarpSyncRequestWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter writer, WarpSyncRequest warpSyncRequest) throws IOException {
        var hashBytes = warpSyncRequest.getBlockHash();

        writer.writeUint256(hashBytes.getBytes());
    }
}
