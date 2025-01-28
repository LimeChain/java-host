package com.limechain.network.protocol.grandpa.messages.catchup.res;

import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import org.apache.tomcat.util.buf.HexUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CatchupResponseMessageScaleReaderTest {

    @Test
    void encodeAndDecodeEqualsTest() throws IOException {
        byte[] expectedByteArr = HexUtils.fromHexString("0401000000000000000100000000000000040a0b0c0d00000000000000000000000000000000000000000000000000000000e7030000010203040000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000506070800000000000000000000000000000000000000000000000000000000040a0b0c0d000000000000000000000000000000000000000000000000000000004d010000010203040000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000506070800000000000000000000000000000000000000000000000000000000700e648a80bf01944ca2a5ae4da4fea86810d02b549d1e399c06eee938b973f101000000");
        ScaleCodecReader reader = new ScaleCodecReader(expectedByteArr);
        CatchUpResMessage commitMessage = reader.read(CatchUpResMessageScaleReader.getInstance());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CatchUpResMessageScaleWriter.getInstance().write(new ScaleCodecWriter(baos), commitMessage);

        assertArrayEquals(expectedByteArr, baos.toByteArray());
    }

}