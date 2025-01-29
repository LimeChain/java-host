package com.limechain.network.protocol.blockannounce.scale;

import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import io.emeraldpay.polkaj.scale.writer.BoolWriter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockAnnounceMessageScaleWriter implements ScaleWriter<BlockAnnounceMessage> {

    private static final BlockAnnounceMessageScaleWriter INSTANCE = new BlockAnnounceMessageScaleWriter();

    public static BlockAnnounceMessageScaleWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter writer, BlockAnnounceMessage message) throws IOException {
        BlockHeaderScaleWriter.getInstance().write(writer, message.getHeader());
        new BoolWriter().write(writer, message.isBestBlock());
    }
}
