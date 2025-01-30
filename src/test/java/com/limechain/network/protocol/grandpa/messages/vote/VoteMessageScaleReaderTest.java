package com.limechain.network.protocol.grandpa.messages.vote;

import com.limechain.grandpa.vote.SubRound;
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

class VoteMessageScaleReaderTest {
    byte[] expectedByteArr = HexUtils.fromHexString("0014000000000000002800000000000000017db9db5ed9967b80143100189ba69d9e4deab85ac3570e5df25686cabe32964a7d00000036e6eca85489bebbb0f687ca5404748d5aa2ffabee34e3ed272cc7b2f6d0a82c65b99bc7cd90dbc21bb528289ebf96705dbd7d96918d34d815509b4e0e2a030f34602b88f60513f1c805d87ef52896934baf6a662bc37414dbdbf69356b1a691");

    private final VoteMessage expectedVoteMsg = new VoteMessage(
            BigInteger.valueOf(20),
            BigInteger.valueOf(40),
            new SignedMessage(
                    SubRound.PRE_COMMIT,
                    Hash256.from("0x7db9db5ed9967b80143100189ba69d9e4deab85ac3570e5df25686cabe32964a"),
                    BigInteger.valueOf(125),
                    Hash512.from("0x36e6eca85489bebbb0f687ca5404748d5aa2ffabee34e3ed272cc7b2f6d0a82c65b99bc7cd90dbc21bb528289ebf96705dbd7d96918d34d815509b4e0e2a030f"),
                    Hash256.from("0x34602b88f60513f1c805d87ef52896934baf6a662bc37414dbdbf69356b1a691")
            )
    );

    @Test
    void decodeEqualsTest() {
        ScaleCodecReader reader = new ScaleCodecReader(expectedByteArr);
        VoteMessage voteMessage = reader.read(VoteMessageScaleReader.getInstance());

        assertEquals(expectedVoteMsg, voteMessage);
    }

    @Test
    void encodeEqualsTest() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        VoteMessageScaleWriter.getInstance().write(new ScaleCodecWriter(baos), expectedVoteMsg);

        assertArrayEquals(expectedByteArr, baos.toByteArray());
    }

}
