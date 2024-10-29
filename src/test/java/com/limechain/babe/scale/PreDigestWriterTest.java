package com.limechain.babe.scale;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.limechain.babe.predigest.PreDigestType;
import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.predigest.scale.PreDigestWriter;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;

public class PreDigestWriterTest {

    private PreDigestWriter preDigestWriter;
    private ScaleCodecWriter writer;

    @BeforeEach
    public void setUp() {
        preDigestWriter = new PreDigestWriter();
        writer = mock(ScaleCodecWriter.class);
    }

    @Test
    public void testWrite_BabePrimaryPreDigest() throws IOException {
        BabePreDigest preDigest = new BabePreDigest(
                PreDigestType.BABE_PRIMARY,
                12345,
                BigInteger.valueOf(67890),
                new byte[]{1, 2, 3},
                new byte[]{4, 5, 6}
        );

        preDigestWriter.write(writer, preDigest);
        verify(writer).writeByte(PreDigestType.BABE_PRIMARY.getValue());
        verify(writer).writeUint32(12345);
        verify(writer).writeUint128(BigInteger.valueOf(67890));
        verify(writer).writeByteArray(new byte[]{1, 2, 3});
        verify(writer).writeByteArray(new byte[]{4, 5, 6});
    }

    @Test
    public void testWrite_BabeSecondaryPlainPreDigest() throws IOException {
        BabePreDigest preDigest = new BabePreDigest(
                PreDigestType.BABE_SECONDARY_PLAIN,
                54321,
                BigInteger.valueOf(98765),
                null,  // VRF output is not used for BABE_SECONDARY_PLAIN
                null   // VRF proof is not used for BABE_SECONDARY_PLAIN
        );
        preDigestWriter.write(writer, preDigest);
        verify(writer).writeByte(PreDigestType.BABE_SECONDARY_PLAIN.getValue());
        verify(writer).writeUint32(54321);
        verify(writer).writeUint128(BigInteger.valueOf(98765));
        verify(writer, never()).writeByteArray(any());
    }

    @Test
    public void testWrite_WithIOException() throws IOException {
        BabePreDigest preDigest = new BabePreDigest(
                PreDigestType.BABE_PRIMARY,
                12345,
                BigInteger.valueOf(67890),
                new byte[]{1, 2, 3},
                new byte[]{4, 5, 6}
        );
        doThrow(IOException.class).when(writer).writeByte(anyByte());
        assertThrows(IOException.class, () -> preDigestWriter.write(writer, preDigest));
    }
}