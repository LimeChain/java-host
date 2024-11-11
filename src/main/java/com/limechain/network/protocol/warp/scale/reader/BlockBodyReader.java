package com.limechain.network.protocol.warp.scale.reader;

import com.limechain.network.protocol.warp.dto.BlockBody;
import com.limechain.transaction.dto.Extrinsic;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.LinkedList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockBodyReader implements ScaleReader<BlockBody> {

    private static final BlockBodyReader INSTANCE = new BlockBodyReader();

    public static BlockBodyReader getInstance() {
        return INSTANCE;
    }

    @Override
    public BlockBody read(ScaleCodecReader reader) {
        List<Extrinsic> extrinsics = new LinkedList<>();

        int extrinsicsCount = reader.readCompactInt();
        for (int i = 0; i < extrinsicsCount; i++) {
            byte[] extrinsic = reader.readByteArray();
            extrinsics.add(new Extrinsic(extrinsic));
        }

        return new BlockBody(extrinsics);
    }
}
