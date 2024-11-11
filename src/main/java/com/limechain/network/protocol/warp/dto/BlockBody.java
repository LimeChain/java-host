package com.limechain.network.protocol.warp.dto;

import com.limechain.network.protocol.warp.scale.writer.BlockBodyWriter;
import com.limechain.transaction.dto.Extrinsic;
import com.limechain.utils.HashUtils;
import com.limechain.utils.scale.ScaleUtils;
import lombok.Data;

import java.util.List;

@Data
public class BlockBody {

    private final List<Extrinsic> extrinsics;

    public byte[][] getExtrinsicsAsByteArray() {
        return extrinsics.stream()
                .map(Extrinsic::getData)
                .toArray(byte[][]::new);
    }

    public byte[] getEncoded(){
        byte[] encoded = ScaleUtils.Encode.encode(BlockBodyWriter.getInstance(), this);
        return HashUtils.hashWithBlake2b(encoded);
    }

}
