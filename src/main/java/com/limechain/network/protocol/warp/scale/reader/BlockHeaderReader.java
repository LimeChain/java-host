package com.limechain.network.protocol.warp.scale.reader;

import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockHeaderReader implements ScaleReader<BlockHeader> {

    private static final BlockHeaderReader INSTANCE = new BlockHeaderReader();

    public static BlockHeaderReader getInstance() {
        return INSTANCE;
    }

    @Override
    public BlockHeader read(ScaleCodecReader reader) {
        BlockHeader blockHeader = new BlockHeader();
        blockHeader.setParentHash(new Hash256(reader.readUint256()));
        // NOTE: Usage of BlockNumberReader is intentionally omitted here,
        //  since we want this to be a compact int, not a var size int
        blockHeader.setBlockNumber(BigInteger.valueOf(reader.readCompactInt()));
        blockHeader.setStateRoot(new Hash256(reader.readUint256()));
        blockHeader.setExtrinsicsRoot(new Hash256(reader.readUint256()));

        var digestCount = reader.readCompactInt();
        HeaderDigest[] digests = new HeaderDigest[digestCount];
        for (int i = 0; i < digestCount; i++) {
            digests[i] = HeaderDigestReader.getInstance().read(reader);
        }

        blockHeader.setDigest(digests);

        return blockHeader;
    }
}
