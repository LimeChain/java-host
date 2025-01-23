package com.limechain.network.protocol.grandpa.messages.commit;

import com.limechain.network.protocol.warp.dto.PreCommit;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.types.Hash256;
import io.emeraldpay.polkaj.types.Hash512;
import org.apache.tomcat.util.buf.HexUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CommitMessageScaleReaderTest {
    byte[] expectedByteArr = HexUtils.fromHexString("011800000000000000000000000000000040ab4c691adf667e76d01f91a3b9f6966bbe731c76d13c7e88d5321947e154bc0e0000000440ab4c691adf667e76d01f91a3b9f6966bbe731c76d13c7e88d5321947e154bc0e00000004efc92540389c72b9c7cfb6fef8c5bff982e3dc689bc43adbfb8609231628957e08f717e933ecc7f4f015652bdef7381368d10590c7ca12f2c7755d423d3f420188dc3417d5058ec4b4503e0c12ea1a0a89be200fe98922423d4334014fa6b0ee");
    private final CommitMessage expectedCommitMsg = new CommitMessage(
            BigInteger.valueOf(24),
            BigInteger.ZERO,
            new Vote(
                    Hash256.from("0x40ab4c691adf667e76d01f91a3b9f6966bbe731c76d13c7e88d5321947e154bc"),
                    BigInteger.valueOf(14)
            ),
            new PreCommit[]{
                    new PreCommit(
                            Hash256.from("0x40ab4c691adf667e76d01f91a3b9f6966bbe731c76d13c7e88d5321947e154bc"),
                            BigInteger.valueOf(14),
                            Hash512.from("0xefc92540389c72b9c7cfb6fef8c5bff982e3dc689bc43adbfb8609231628957e08f717e933ecc7f4f015652bdef7381368d10590c7ca12f2c7755d423d3f4201"),
                            Hash256.from("0x88dc3417d5058ec4b4503e0c12ea1a0a89be200fe98922423d4334014fa6b0ee")
                    )
            }
    );

    @Test
    void decodeEqualsTest() {
        ScaleCodecReader reader = new ScaleCodecReader(expectedByteArr);
        CommitMessage commitMessage = reader.read(CommitMessageScaleReader.getInstance());

        assertEquals(expectedCommitMsg, commitMessage);
    }

    @Test
    void encodeEqualsTest() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CommitMessageScaleWriter.getInstance().write(new ScaleCodecWriter(baos), expectedCommitMsg);

        assertArrayEquals(expectedByteArr, baos.toByteArray());
    }

}
