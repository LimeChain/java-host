package com.limechain.network.protocol.blockannounce.scale;

import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceHandshake;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockAnnounceHandshakeScaleWriter implements ScaleWriter<BlockAnnounceHandshake> {

    private static final BlockAnnounceHandshakeScaleWriter INSTANCE = new BlockAnnounceHandshakeScaleWriter();

    public static BlockAnnounceHandshakeScaleWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter writer, BlockAnnounceHandshake handshake) throws IOException {
        writer.writeByte(handshake.getNodeRole());
        writer.writeUint32(handshake.getBestBlock().longValue());
        writer.writeUint256(handshake.getBestBlockHash().getBytes());
        writer.writeUint256(handshake.getGenesisBlockHash().getBytes());
    }
}
