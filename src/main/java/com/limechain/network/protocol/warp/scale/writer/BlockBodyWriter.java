package com.limechain.network.protocol.warp.scale.writer;

import com.limechain.network.protocol.warp.dto.BlockBody;
import com.limechain.transaction.dto.Extrinsic;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockBodyWriter implements ScaleWriter<BlockBody> {

    private static final BlockBodyWriter INSTANCE = new BlockBodyWriter();

    public static BlockBodyWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter writer, BlockBody blockBody) throws IOException {
        List<Extrinsic> extrinsics = blockBody.getExtrinsics();

        writer.writeCompact(extrinsics.size());
        for (Extrinsic extrinsic : extrinsics) {
            writer.writeAsList(extrinsic.getData());
        }
    }
}
