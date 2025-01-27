package com.limechain.babe.scale;

import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.predigest.PreDigestType;
import com.limechain.babe.predigest.scale.PreDigestReader;
import com.limechain.babe.predigest.scale.PreDigestWriter;
import com.limechain.utils.StringUtils;
import com.limechain.utils.scale.ScaleUtils;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static io.emeraldpay.polkaj.schnorrkel.VrfOutputAndProof.OUTPUT_BYTE_LEN;
import static io.emeraldpay.polkaj.schnorrkel.VrfOutputAndProof.PROOF_BYTE_LEN;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PreDigestScaleTest {

    @Test
    public void testEncodeAndDecode_BABE_SECONDARY_VRF() {
        byte[] bytes = StringUtils.hexToBytes("0x03b7010000286f301100000000424dc7bc71c9b0f3a15b6aba389471fff57e154dbf3dc046f47513a38375ce4cfe421df446b2b7a0f5b1b2e69c810a6ba36787c0301e6636df3e45688116e005363dfe9922b3b7cc7b80e2fdab8b0b98dcfb4b96cc470fb35f753debda40aa0e");
        BabePreDigest preDigest = ScaleUtils.Decode.decode(bytes, new PreDigestReader());
        assertEquals(PreDigestType.BABE_SECONDARY_VRF, preDigest.getType());
        assertEquals(439, preDigest.getAuthorityIndex());
        assertEquals(BigInteger.valueOf(288386856), preDigest.getSlotNumber());
        byte[] vrfOutput = preDigest.getVrfOutput();
        byte[] vrfProof = preDigest.getVrfProof();
        assertNotNull(vrfOutput);
        assertNotNull(vrfProof);
        assertEquals(OUTPUT_BYTE_LEN, vrfOutput.length);
        assertEquals(PROOF_BYTE_LEN, vrfProof.length);

        byte[] encode = ScaleUtils.Encode.encode(new PreDigestWriter(), preDigest);
        assertArrayEquals(bytes, encode);
    }
}
