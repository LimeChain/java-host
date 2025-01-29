package com.limechain.network.protocol.blockannounce.scale;

import com.google.protobuf.ByteString;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceHandshake;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.types.Hash256;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockAnnounceHandshakeScaleReaderTest {

    @Test
    void decodeHandshake() {
        ByteString byteString = ByteString.fromHex("044d00000001000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000");
        byte[] encodedBytes = byteString.toByteArray();

        ScaleCodecReader reader = new ScaleCodecReader(encodedBytes);
        BlockAnnounceHandshake decoded = reader.read(new BlockAnnounceHandshakeScaleReader());

        assertEquals(4, decoded.getNodeRole());
        assertEquals(BigInteger.valueOf(77), decoded.getBestBlock());
        assertEquals("0x0100000000000000000000000000000000000000000000000000000000000000",
                decoded.getBestBlockHash().toString());
        assertEquals("0x0200000000000000000000000000000000000000000000000000000000000000",
                decoded.getGenesisBlockHash().toString());
    }

    @Test
    void EncodeAnnouncementHandshakeTest() {
        byte[] expected = ByteString.fromHex("044d00000001000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000").toByteArray();

        BlockAnnounceHandshake dataToEncode = new BlockAnnounceHandshake();
        dataToEncode.setNodeRole(4);
        dataToEncode.setBestBlock(BigInteger.valueOf(77));
        dataToEncode.setBestBlockHash(Hash256.from(
                "0x0100000000000000000000000000000000000000000000000000000000000000"));
        dataToEncode.setGenesisBlockHash(Hash256.from(
                "0x0200000000000000000000000000000000000000000000000000000000000000"));

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ScaleCodecWriter writer = new ScaleCodecWriter(buf)) {
            writer.write(BlockAnnounceHandshakeScaleWriter.getInstance(), dataToEncode);
            byte[] encoded = buf.toByteArray();

            assertTrue(Arrays.equals(encoded, expected));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}