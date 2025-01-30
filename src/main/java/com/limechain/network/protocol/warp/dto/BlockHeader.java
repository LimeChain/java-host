package com.limechain.network.protocol.warp.dto;

import com.limechain.network.protocol.blockannounce.scale.BlockHeaderScaleWriter;
import com.limechain.network.protocol.warp.scale.reader.BlockHeaderReader;
import com.limechain.utils.HashUtils;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;

@Setter
@Getter
public class BlockHeader implements Serializable {
    // TODO: Make this const configurable
    public static final int BLOCK_NUMBER_SIZE = 4;

    private Hash256 parentHash;
    private BigInteger blockNumber;
    private Hash256 stateRoot;
    private Hash256 extrinsicsRoot;
    private HeaderDigest[] digest;

    @Override
    public String toString() {
        return "BlockHeader{" +
                "parentHash=" + parentHash +
                ", blockNumber=" + blockNumber +
                ", stateRoot=" + stateRoot +
                ", extrinsicsRoot=" + extrinsicsRoot +
                ", digest=" + Arrays.toString(digest) +
                '}';
    }

    public Hash256 getHash() {
        return new Hash256(getBlake2bHash(true));
    }

    public byte[] getBlake2bHash(boolean sealed) {
        byte[] scaleEncoded = ScaleUtils.Encode.encode(sealed
                        ? BlockHeaderScaleWriter.getInstance()
                        : BlockHeaderScaleWriter.getInstance()::writeUnsealed,
                this);
        return HashUtils.hashWithBlake2b(scaleEncoded);
    }

    public static BlockHeader fromHash(Hash256 hash) {
        return ScaleUtils.Decode.decode(hash.getBytes(), BlockHeaderReader.getInstance());
    }
}
