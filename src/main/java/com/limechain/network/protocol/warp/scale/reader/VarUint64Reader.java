package com.limechain.network.protocol.warp.scale.reader;

import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Decodes into a `u64` a SCALE-encoded number whose number of bytes isn't known at compile-time.
 * Code translated from:
 * <a href="https://github.com/smol-dot/smoldot/blob/165412f0292009aedd208615a37cf2859fd45936/lib/src/util.rs#L95">
 * nom_varsize_number_decode_u64</a>
 */
public class VarUint64Reader implements ScaleReader<BigInteger> {

    private final int blockNumSize;

    public VarUint64Reader(int blockNumSize) {
        this.blockNumSize = blockNumSize;
    }

    @Override
    public BigInteger read(ScaleCodecReader reader) {
        byte[] input = reader.readByteArray(blockNumSize);
        byte[] slice = new byte[8];
        System.arraycopy(input, 0, slice, 0, blockNumSize);

        ByteBuffer buffer = ByteBuffer.wrap(slice);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        return BigInteger.valueOf(buffer.getLong());
    }
}
