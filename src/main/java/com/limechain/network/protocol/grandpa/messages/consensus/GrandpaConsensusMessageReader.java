package com.limechain.network.protocol.grandpa.messages.consensus;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.chain.lightsyncstate.scale.AuthorityReader;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.ListReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;

import java.math.BigInteger;
import java.util.List;

public class GrandpaConsensusMessageReader implements ScaleReader<GrandpaConsensusMessage> {

    @Override
    public GrandpaConsensusMessage read(ScaleCodecReader reader) {
        GrandpaConsensusMessage grandpaConsensusMessage = new GrandpaConsensusMessage();
        GrandpaConsensusMessageFormat format = GrandpaConsensusMessageFormat.fromFormat(reader.readByte());
        grandpaConsensusMessage.setFormat(format);
        switch (format) {
            case GRANDPA_SCHEDULED_CHANGE -> {
                List<Authority> authorities = reader.read(new ListReader<>(new AuthorityReader()));
                long delay = reader.readUint32();
                grandpaConsensusMessage.setAuthorities(authorities);
                grandpaConsensusMessage.setDelay(delay);
            }
            case GRANDPA_FORCED_CHANGE -> {
                long delayStartBlockNumber = reader.readUint32();
                List<Authority> authorities = reader.read(new ListReader<>(new AuthorityReader()));
                long delay = reader.readUint32();
                grandpaConsensusMessage.setDelayStartBlockNumber(BigInteger.valueOf(delayStartBlockNumber));
                grandpaConsensusMessage.setAuthorities(authorities);
                grandpaConsensusMessage.setDelay(delay);
            }
            case GRANDPA_ON_DISABLED -> grandpaConsensusMessage.setDisabledAuthority(new UInt64Reader().read(reader));
            case GRANDPA_PAUSE, GRANDPA_RESUME -> grandpaConsensusMessage.setDelay(reader.readUint32());
        }
        return grandpaConsensusMessage;
    }
}
