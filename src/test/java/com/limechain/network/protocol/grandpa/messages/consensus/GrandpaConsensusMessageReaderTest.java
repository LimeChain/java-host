package com.limechain.network.protocol.grandpa.messages.consensus;

import com.limechain.utils.StringUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GrandpaConsensusMessageReaderTest {

    private final GrandpaConsensusMessageReader reader = new GrandpaConsensusMessageReader();

    @Test
    void testScheduledChangeInput() {
        String hexWithPrefix = "0x0104010101010101010101010101010101010101010101010101010101010101010101020000000000000003000000";
        byte[] input = StringUtils.hexToBytes(hexWithPrefix);
        GrandpaConsensusMessage message = reader.read(new ScaleCodecReader(input));
        assertNotNull(message);
        assertEquals(GrandpaConsensusMessageFormat.GRANDPA_SCHEDULED_CHANGE, message.getFormat());
        assertNotNull(message.getAuthorities());
        assertEquals(1, message.getAuthorities().size());
        assertEquals(BigInteger.valueOf(768L), message.getDelay());
    }

    @Test
    void testForcedChangeInput() {
        String hexWithPrefix = "0x020300000004010101010101010101010101010101010101010101010101010101010101010101020000000000000003000000";
        byte[] input = StringUtils.hexToBytes(hexWithPrefix);
        GrandpaConsensusMessage message = reader.read(new ScaleCodecReader(input));
        assertNotNull(message);
        assertEquals(GrandpaConsensusMessageFormat.GRANDPA_FORCED_CHANGE, message.getFormat());
        assertEquals(BigInteger.valueOf(3), message.getDelayStartBlockNumber());
        assertNotNull(message.getAuthorities());

        assertEquals(1, message.getAuthorities().size());
        assertEquals(BigInteger.valueOf(768L), message.getDelay());
    }

    @Test
    void testOnDisabledInput() {
        String hexWithPrefix = "0x0315cd5b0700000000";
        byte[] input = StringUtils.hexToBytes(hexWithPrefix);
        GrandpaConsensusMessage message = reader.read(new ScaleCodecReader(input));
        assertNotNull(message);
        assertEquals(GrandpaConsensusMessageFormat.GRANDPA_ON_DISABLED, message.getFormat());
        assertEquals(BigInteger.valueOf(123456789L), message.getDisabledAuthority());
    }

    @Test
    void testPauseInput() {
        String hexWithPrefix = "0x0414000000";
        byte[] input = StringUtils.hexToBytes(hexWithPrefix);
        GrandpaConsensusMessage message = reader.read(new ScaleCodecReader(input));
        assertNotNull(message);
        assertEquals(GrandpaConsensusMessageFormat.GRANDPA_PAUSE, message.getFormat());
        assertEquals(BigInteger.valueOf(20L), message.getDelay());
    }

    @Test
    void testResumeInput() {
        String hexWithPrefix = "0x0519000000";
        byte[] input = StringUtils.hexToBytes(hexWithPrefix);
        GrandpaConsensusMessage message = reader.read(new ScaleCodecReader(input));
        assertNotNull(message);
        assertEquals(GrandpaConsensusMessageFormat.GRANDPA_RESUME, message.getFormat());
        assertEquals(BigInteger.valueOf(25L), message.getDelay());
    }
}
