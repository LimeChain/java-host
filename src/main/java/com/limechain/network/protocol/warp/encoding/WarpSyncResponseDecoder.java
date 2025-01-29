package com.limechain.network.protocol.warp.encoding;

import com.limechain.network.protocol.warp.dto.WarpSyncResponse;
import com.limechain.network.protocol.warp.scale.reader.WarpSyncResponseScaleReader;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class WarpSyncResponseDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> out) {
        byte[] messageBytes = new byte[in.readableBytes()];
        in.readBytes(messageBytes);
        WarpSyncResponse response = WarpSyncResponseScaleReader.getInstance().read(new ScaleCodecReader(messageBytes));
        out.add(response);
    }
}
