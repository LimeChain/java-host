package com.limechain.babe.scale;

import com.limechain.babe.api.BabeApiConfiguration;
import com.limechain.babe.api.scale.BabeApiConfigurationReader;
import com.limechain.chain.lightsyncstate.BabeEpoch;
import com.limechain.utils.StringUtils;
import com.limechain.utils.scale.ScaleUtils;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BabeApiConfigurationReaderTest {
    private static final String BABE_API_CONFIGURATION_HEX = "0x701700000000000060090000000000000100000000000000040000000000000018fa3437" +
            "b10f6e7af8f31362df3a179b991a8c56313d1bcd6307a4d0c734c1ae310100000000000000d2419bc8835493ac89eb09d5985281f5dff4bc6c7a7ea988fd23af" +
            "05f301580a0100000000000000ccb6bef60defc30724545d57440394ed1c71ea7ee6d880ed0e79871a05b5e40601000000000000005e67b64cf07d4d258a47df" +
            "63835121423551712844f5b67de68e36bb9a21e12701000000000000006236877b05370265640c133fec07e64d7ca823db1dc56f2d3584b3d7c0f16158010000" +
            "00000000006c52d02d95c30aa567fda284acf25025ca7470f0b0c516ddf94475a1807c4d25010000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000002";

    @Test
    void decodeBabeApiConfigurationTest() {
        byte[] data = StringUtils.hexToBytes(BABE_API_CONFIGURATION_HEX);
        BabeApiConfiguration babeApiConfiguration = ScaleUtils.Decode.decode(data, BabeApiConfigurationReader.getInstance());
        assertEquals(BigInteger.valueOf(6000), babeApiConfiguration.getSlotDuration());
        assertEquals(BigInteger.valueOf(2400), babeApiConfiguration.getEpochLength());
        assertNotNull(babeApiConfiguration.getConstant());
        assertEquals(BigInteger.valueOf(1), babeApiConfiguration.getConstant().getValue0());
        assertEquals(BigInteger.valueOf(4), babeApiConfiguration.getConstant().getValue1());
        assertEquals(6, babeApiConfiguration.getAuthorities().size());
        assertEquals(BabeEpoch.BabeAllowedSlots.PRIMARY_AND_SECONDARY_VRF_SLOTS, babeApiConfiguration.getAllowedSlots());
    }
}
