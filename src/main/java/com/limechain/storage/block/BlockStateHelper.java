package com.limechain.storage.block;

import com.limechain.exception.scale.ScaleEncodingException;
import com.limechain.network.protocol.blockannounce.scale.BlockHeaderScaleWriter;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.scale.reader.BlockHeaderReader;
import com.limechain.storage.DBConstants;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;
import io.emeraldpay.polkaj.scale.writer.UInt64Writer;
import io.emeraldpay.polkaj.types.Hash256;
import org.javatuples.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class BlockStateHelper {
    private final UInt64Writer uint64Writer = new UInt64Writer();
    private final UInt64Reader uint64Reader = new UInt64Reader();

    @NotNull
    public String headerKey(Hash256 key) {
        String headerPrefix = "hdr";
        return headerPrefix.concat(key.toString());
    }

    @NotNull
    public String blockBodyKey(Hash256 key) {
        String blockBodyPrefix = "blb";
        return blockBodyPrefix.concat(key.toString());
    }

    @NotNull
    public String headerHashKey(BigInteger block) {
        String headerHashPrefix = "hsh";
        return headerHashPrefix.concat(new String(bigIntegersToByteArray(block)));
    }

    @NotNull
    public String arrivalTimeKey(Hash256 key) {
        String arrivalTimePrefix = "arr";
        return arrivalTimePrefix.concat(key.toString());
    }

    @NotNull
    public String finalizedHashKey(BigInteger round, BigInteger setId) {
        return DBConstants
                .FINALIZED_BLOCK_KEY
                .concat(new String(bigIntegersToByteArray(round, setId)));
    }

    byte[] bigIntegersToByteArray(BigInteger... values) {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final ScaleCodecWriter scaleCodecWriter = new ScaleCodecWriter(baos)) {

            for (BigInteger value : values) {
                uint64Writer.write(scaleCodecWriter, value);
            }

            return baos.toByteArray();
        } catch (IOException e) {
            final ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            for (BigInteger value : values) {
                buffer.putLong(value.longValue());
            }
            return buffer.array();
        }
    }

    @NotNull
    Pair<BigInteger, BigInteger> bytesToRoundAndSetId(final byte[] bytes) {
        final ScaleCodecReader scaleCodecReader = new ScaleCodecReader(bytes);

        BigInteger round = uint64Reader.read(scaleCodecReader);
        BigInteger setId = uint64Reader.read(scaleCodecReader);

        return new Pair<>(round, setId);
    }

    @NotNull
    byte[] writeHeader(BlockHeader header) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ScaleCodecWriter scaleCodecWriter = new ScaleCodecWriter(baos)) {
            BlockHeaderScaleWriter.getInstance().write(scaleCodecWriter, header);

            return baos.toByteArray();
        } catch (IOException e) {
            throw new ScaleEncodingException(e);
        }
    }

    @NotNull
    BlockHeader readHeader(byte[] header) {
        ScaleCodecReader scaleCodecReader = new ScaleCodecReader(header);
        BlockHeaderReader blockHeaderReader = new BlockHeaderReader();

        return blockHeaderReader.read(scaleCodecReader);
    }
}
