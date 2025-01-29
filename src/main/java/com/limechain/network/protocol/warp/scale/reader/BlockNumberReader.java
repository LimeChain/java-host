package com.limechain.network.protocol.warp.scale.reader;

import com.limechain.network.protocol.warp.dto.BlockHeader;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;

import java.math.BigInteger;

/**
 * A reader for a block's number for the places where this block number
 * has been encoded as a varint instead of compact int.
 * Varint meaning variable length integer, i.e. its byte size may vary.
 */
public class BlockNumberReader implements ScaleReader<BigInteger> {

    private static final BlockNumberReader INSTANCE = new BlockNumberReader();

    private final VarUint64Reader varUint64Reader;

    private BlockNumberReader() {
        this.varUint64Reader = new VarUint64Reader(BlockHeader.BLOCK_NUMBER_SIZE);
    }

    public static BlockNumberReader getInstance() {
        return INSTANCE;
    }

    @Override
    public BigInteger read(ScaleCodecReader reader) {
        return varUint64Reader.read(reader);
    }
}
