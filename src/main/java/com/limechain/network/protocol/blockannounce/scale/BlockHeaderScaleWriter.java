package com.limechain.network.protocol.blockannounce.scale;

import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import io.emeraldpay.polkaj.scale.writer.ListWriter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockHeaderScaleWriter implements ScaleWriter<BlockHeader> {

    private static final BlockHeaderScaleWriter INSTANCE = new BlockHeaderScaleWriter();

    private final HeaderDigestScaleWriter headerDigestScaleWriter = HeaderDigestScaleWriter.getInstance();

    public static BlockHeaderScaleWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter writer, BlockHeader blockHeader) throws IOException {
        write(writer, blockHeader, true);
    }

    public void writeUnsealed(ScaleCodecWriter writer, BlockHeader blockHeader) throws IOException {
        write(writer, blockHeader, false);
    }

    private void write(ScaleCodecWriter writer, BlockHeader blockHeader, boolean sealed) throws IOException {
        writer.writeUint256(blockHeader.getParentHash().getBytes());
        // NOTE: Usage of BlockNumberWriter is intentionally omitted here,
        //  since we want this to be a compact int, not a var size int
        writer.writeCompact(blockHeader.getBlockNumber().intValueExact());
        writer.writeUint256(blockHeader.getStateRoot().getBytes());
        writer.writeUint256(blockHeader.getExtrinsicsRoot().getBytes());

        // filter out the seal if we're writing unsealed
        List<HeaderDigest> digestItems = Arrays.stream(blockHeader.getDigest())
            .filter(digest -> sealed || digest.getType() != DigestType.SEAL)
            .toList();

        new ListWriter<>(headerDigestScaleWriter).write(writer, digestItems);
    }
}
