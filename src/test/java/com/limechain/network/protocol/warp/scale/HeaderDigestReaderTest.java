package com.limechain.network.protocol.warp.scale;

import com.google.protobuf.ByteString;
import com.limechain.network.protocol.warp.dto.ConsensusEngine;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.network.protocol.warp.scale.reader.HeaderDigestReader;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HeaderDigestReaderTest {
    @Test
    void decodeMultipleDigests() {
        String encoded = "0c0642414245340201000000ef55a50f00000000044241424549040118ca239392960473fe1bc65f94ee27d890a49c1b200c006ff5dcc525330ecc16770100000000000000b46f01874ce7abbb5220e8fd89bede0adad14c73039d91e28e881823433e723f0100000000000000d684d9176d6eb69887540c9a89fa6097adea82fc4b0ff26d1062b488f352e179010000000000000068195a71bdde49117a616424bdc60a1733e96acb1da5aeab5d268cf2a572e94101000000000000001a0575ef4ae24bdfd31f4cb5bd61239ae67c12d4e64ae51ac756044aa6ad8200010000000000000018168f2aad0081a25728961ee00627cfe35e39833c805016632bf7c14da5800901000000000000000000000000000000000000000000000000000000000000000000000000000000054241424501014625284883e564bc1e4063f5ea2b49846cdddaa3761d04f543b698c1c3ee935c40d25b869247c36c6b8a8cbbd7bb2768f560ab7c276df3c62df357a7e3b1ec8d";
        byte[] bytes = ByteString.fromHex(encoded).toByteArray();
        ScaleCodecReader reader = new ScaleCodecReader(bytes);

        int len = reader.readCompactInt();
        assertEquals(3, len);

        for (int i = 0; i < len; i++) {
            HeaderDigest decoded = HeaderDigestReader.getInstance().read(reader);
            switch (i) {
                case 0 -> {
                    assertEquals(DigestType.PRE_RUNTIME, decoded.getType());
                    assertEquals(ConsensusEngine.BABE, decoded.getId());
                    assertArrayEquals(new byte[]{2, 1, 0, 0, 0, -17, 85, -91, 15, 0, 0, 0, 0}, decoded.getMessage());
                }
                case 1 -> {
                    assertEquals(DigestType.CONSENSUS_MESSAGE, decoded.getType());
                    assertEquals(ConsensusEngine.BABE, decoded.getId());
                    assertArrayEquals(new byte[]{1, 24, -54, 35, -109, -110, -106, 4, 115, -2, 27, -58, 95, -108, -18, 39, -40, -112, -92, -100, 27, 32, 12, 0, 111, -11, -36, -59, 37, 51, 14, -52, 22, 119, 1, 0, 0, 0, 0, 0, 0, 0, -76, 111, 1, -121, 76, -25, -85, -69, 82, 32, -24, -3, -119, -66, -34, 10, -38, -47, 76, 115, 3, -99, -111, -30, -114, -120, 24, 35, 67, 62, 114, 63, 1, 0, 0, 0, 0, 0, 0, 0, -42, -124, -39, 23, 109, 110, -74, -104, -121, 84, 12, -102, -119, -6, 96, -105, -83, -22, -126, -4, 75, 15, -14, 109, 16, 98, -76, -120, -13, 82, -31, 121, 1, 0, 0, 0, 0, 0, 0, 0, 104, 25, 90, 113, -67, -34, 73, 17, 122, 97, 100, 36, -67, -58, 10, 23, 51, -23, 106, -53, 29, -91, -82, -85, 93, 38, -116, -14, -91, 114, -23, 65, 1, 0, 0, 0, 0, 0, 0, 0, 26, 5, 117, -17, 74, -30, 75, -33, -45, 31, 76, -75, -67, 97, 35, -102, -26, 124, 18, -44, -26, 74, -27, 26, -57, 86, 4, 74, -90, -83, -126, 0, 1, 0, 0, 0, 0, 0, 0, 0, 24, 22, -113, 42, -83, 0, -127, -94, 87, 40, -106, 30, -32, 6, 39, -49, -29, 94, 57, -125, 60, -128, 80, 22, 99, 43, -9, -63, 77, -91, -128, 9, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                            decoded.getMessage());
                }
                case 2 -> {
                    assertEquals(DigestType.SEAL, decoded.getType());
                    assertEquals(ConsensusEngine.BABE, decoded.getId());
                    assertArrayEquals(new byte[]{70, 37, 40, 72, -125, -27, 100, -68, 30, 64, 99, -11, -22, 43, 73, -124, 108, -35, -38, -93, 118, 29, 4, -11, 67, -74, -104, -63, -61, -18, -109, 92, 64, -46, 91, -122, -110, 71, -61, 108, 107, -118, -116, -69, -41, -69, 39, 104, -11, 96, -85, 124, 39, 109, -13, -58, 45, -13, 87, -89, -29, -79, -20, -115},
                            decoded.getMessage());
                }
            }
        }

    }

    @Test
    void decodePreRuntime() {
        String encoded = "06424142451001030507";
        ByteString byteString = ByteString.fromHex(encoded);
        byte[] bytes = byteString.toByteArray();
        HeaderDigest decoded = HeaderDigestReader.getInstance().read(new ScaleCodecReader(bytes));

        assertEquals(ConsensusEngine.BABE, decoded.getId());
        assertEquals(DigestType.PRE_RUNTIME, decoded.getType());
        assertArrayEquals(new byte[]{1, 3, 5, 7}, decoded.getMessage());
    }

    @Test
    void decodeConsensusDigest() {
        String encoded = "04424142451001030507";
        ByteString byteString = ByteString.fromHex(encoded);
        byte[] bytes = byteString.toByteArray();
        HeaderDigest decoded = HeaderDigestReader.getInstance().read(new ScaleCodecReader(bytes));

        assertEquals(ConsensusEngine.BABE, decoded.getId());
        assertEquals(DigestType.CONSENSUS_MESSAGE, decoded.getType());
        assertArrayEquals(new byte[]{1, 3, 5, 7}, decoded.getMessage());
    }

    @Test
    void decodeSealDigest() {
        String encoded = "05424142451001030507";
        ByteString byteString = ByteString.fromHex(encoded);
        byte[] bytes = byteString.toByteArray();
        HeaderDigest decoded = HeaderDigestReader.getInstance().read(new ScaleCodecReader(bytes));

        assertEquals(ConsensusEngine.BABE, decoded.getId());
        assertEquals(DigestType.SEAL, decoded.getType());
        assertArrayEquals(new byte[]{1, 3, 5, 7}, decoded.getMessage());
    }

}