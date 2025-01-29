package com.limechain.network.protocol.grandpa.messages.consensus;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.chain.lightsyncstate.scale.AuthorityReader;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.ListReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GrandpaConsensusMessageReader implements ScaleReader<GrandpaConsensusMessage> {

    private static final GrandpaConsensusMessageReader INSTANCE = new GrandpaConsensusMessageReader();

    public static GrandpaConsensusMessageReader getInstance() {
        return INSTANCE;
    }

    @Override
    public GrandpaConsensusMessage read(ScaleCodecReader reader) {

        AuthorityReader authorityReader = AuthorityReader.getInstance();

        GrandpaConsensusMessage grandpaConsensusMessage = new GrandpaConsensusMessage();
        GrandpaConsensusMessageFormat format = GrandpaConsensusMessageFormat.fromFormat(reader.readByte());
        grandpaConsensusMessage.setFormat(format);

        switch (format) {
            case GRANDPA_SCHEDULED_CHANGE -> {
                List<Authority> authorities = reader.read(new ListReader<>(authorityReader));
                BigInteger delay = BigInteger.valueOf(reader.readUint32());

                grandpaConsensusMessage.setAuthorities(authorities);
                grandpaConsensusMessage.setDelay(delay);
            }
            case GRANDPA_FORCED_CHANGE -> {
                BigInteger additionalOffset = BigInteger.valueOf(reader.readUint32());
                List<Authority> authorities = reader.read(new ListReader<>(authorityReader));
                BigInteger delay = BigInteger.valueOf(reader.readUint32());

                grandpaConsensusMessage.setAuthorities(authorities);
                grandpaConsensusMessage.setDelay(delay);
                grandpaConsensusMessage.setAdditionalOffset(additionalOffset);
            }
            case GRANDPA_ON_DISABLED -> grandpaConsensusMessage.setDisabledAuthority(new UInt64Reader().read(reader));
            case GRANDPA_PAUSE, GRANDPA_RESUME -> grandpaConsensusMessage.setDelay(
                    BigInteger.valueOf(reader.readUint32())
            );
        }

        return grandpaConsensusMessage;
    }
}
