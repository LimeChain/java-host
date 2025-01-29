package com.limechain.network.protocol.warp.scale.writer;

import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;

import java.io.IOException;
import java.math.BigInteger;

/**
 * Encodes a `u64` into a SCALE-encoded number whose number of bytes isn't known at compile-time.
 */
public class VarUint64Writer implements ScaleWriter<BigInteger> {

    private final int blockNumSize;

    public VarUint64Writer(int blockNumSize) {
        this.blockNumSize = blockNumSize;
    }

    @Override
    public void write(ScaleCodecWriter wrt, BigInteger value) throws IOException {
        if (value.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Negative values are not supported: " + value);
        }
        if (blockNumSize > 0) {
            wrt.directWrite(value.and(BigInteger.valueOf(255L)).intValueExact());
            for (int i = 1; i < blockNumSize; i++) {
                wrt.directWrite(value.shiftRight(8 * i).and(BigInteger.valueOf(255L)).intValueExact());
            }
        }
    }
}
