package com.limechain.network.protocol.warp.dto;

import com.limechain.network.protocol.blockannounce.scale.BlockHeaderScaleWriter;
import com.limechain.utils.HashUtils;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.util.Arrays;

@Setter
@Getter
public class BlockHeader {
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
        return new Hash256(getHashBytes());
    }

    public byte[] getHashBytes() {
        byte[] scaleEncoded = ScaleUtils.Encode.encode(BlockHeaderScaleWriter.getInstance(), this);
        return HashUtils.hashWithBlake2b(scaleEncoded);
    }
}
