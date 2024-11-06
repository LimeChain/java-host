package com.limechain.babe.scale;

import com.limechain.babe.consensus.BabeConsensusMessage;
import com.limechain.babe.consensus.BabeConsensusMessageFormat;
import com.limechain.babe.consensus.scale.BabeConsensusMessageReader;
import com.limechain.chain.lightsyncstate.BabeEpoch;
import com.limechain.utils.StringUtils;
import com.limechain.utils.scale.ScaleUtils;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BabeConsensusMessageReaderTest {

    @Test
    public void testReadFormatOne() {
        byte[] bytes = StringUtils.hexToBytes("0x0118fa3437b10f6e7af8f31362df3a179b991a8c56313d1bcd6307a4d0c734c1ae310100000000000000d2419bc8835493ac89eb09d5985281f5dff4bc6c7a7ea988fd23af05f301580a0100000000000000ccb6bef60defc30724545d57440394ed1c71ea7ee6d880ed0e79871a05b5e40601000000000000005e67b64cf07d4d258a47df63835121423551712844f5b67de68e36bb9a21e12701000000000000006236877b05370265640c133fec07e64d7ca823db1dc56f2d3584b3d7c0f1615801000000000000006c52d02d95c30aa567fda284acf25025ca7470f0b0c516ddf94475a1807c4d250100000000000000e7ac246335e99022128526f58d52b6d24bb5adc253536c4940d365463ec25e4d");
        BabeConsensusMessage babeConsensusMessage = ScaleUtils.Decode.decode(bytes, new BabeConsensusMessageReader());
        assertEquals(BabeConsensusMessageFormat.NEXT_EPOCH_DATA, babeConsensusMessage.getFormat());
        assertNotNull(babeConsensusMessage.getNextEpochData());
        assertNotNull(babeConsensusMessage.getNextEpochData().getAuthorities());
        assertEquals(6, babeConsensusMessage.getNextEpochData().getAuthorities().size());
        assertNotNull(babeConsensusMessage.getNextEpochData().getRandomness());
    }

    @Test
    public void testReadFormatThree() {
        byte[] bytes = StringUtils.hexToBytes("0x03010100000000000000040000000000000002");
        BabeConsensusMessage babeConsensusMessage = ScaleUtils.Decode.decode(bytes, new BabeConsensusMessageReader());
        assertEquals(BabeConsensusMessageFormat.NEXT_EPOCH_DESCRIPTOR, babeConsensusMessage.getFormat());
        assertNotNull(babeConsensusMessage.getNextEpochDescriptor());
        assertEquals(BabeEpoch.BabeAllowedSlots.PRIMARY_SLOTS, babeConsensusMessage.getNextEpochDescriptor().getAllowedSlots());
        assertNotNull(babeConsensusMessage.getNextEpochDescriptor().getConstant());
        assertEquals(BigInteger.valueOf(257),babeConsensusMessage.getNextEpochDescriptor().getConstant().getValue0());
        assertEquals(BigInteger.valueOf(1024),babeConsensusMessage.getNextEpochDescriptor().getConstant().getValue1());
    }
}
