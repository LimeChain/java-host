package com.limechain.network.protocol.blockannounce.scale;

import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import io.emeraldpay.polkaj.scale.writer.BoolWriter;

import java.io.IOException;

public class BlockAnnounceMessageScaleWriter implements ScaleWriter<BlockAnnounceMessage> {
    @Override
    public void write(ScaleCodecWriter writer, BlockAnnounceMessage message) throws IOException {
        BlockHeaderScaleWriter.getInstance().write(writer, message.getHeader());
        new BoolWriter().write(writer, message.isBestBlock());
    }
}
