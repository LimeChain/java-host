package com.limechain.network.protocol.blockannounce.scale;

import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.warp.scale.reader.BlockHeaderReader;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockAnnounceMessageScaleReader implements ScaleReader<BlockAnnounceMessage> {

    private static final BlockAnnounceMessageScaleReader INSTANCE = new BlockAnnounceMessageScaleReader();

    public static BlockAnnounceMessageScaleReader getInstance() {
        return INSTANCE;
    }

    @Override
    public BlockAnnounceMessage read(ScaleCodecReader reader) {
        BlockAnnounceMessage message = new BlockAnnounceMessage();
        message.setHeader(BlockHeaderReader.getInstance().read(reader));
        message.setBestBlock(reader.readBoolean());
        return message;
    }
}
