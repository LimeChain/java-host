package com.limechain.babe.consensus.scale;

import com.limechain.babe.consensus.BabeConsensusMessage;
import com.limechain.babe.consensus.BabeConsensusMessageFormat;
import com.limechain.babe.state.EpochData;
import com.limechain.babe.state.EpochDescriptor;
import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.chain.lightsyncstate.BabeEpoch;
import com.limechain.chain.lightsyncstate.scale.AuthorityReader;
import com.limechain.utils.scale.readers.PairReader;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.EnumReader;
import io.emeraldpay.polkaj.scale.reader.ListReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.javatuples.Pair;

import java.math.BigInteger;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BabeConsensusMessageReader implements ScaleReader<BabeConsensusMessage> {

    private static final BabeConsensusMessageReader INSTANCE = new BabeConsensusMessageReader();

    public static BabeConsensusMessageReader getInstance() {
        return INSTANCE;
    }

    @Override
    public BabeConsensusMessage read(ScaleCodecReader reader) {
        BabeConsensusMessage babeConsensusMessage = new BabeConsensusMessage();
        BabeConsensusMessageFormat format = BabeConsensusMessageFormat.fromFormat(reader.readByte());
        babeConsensusMessage.setFormat(format);

        switch (format) {
            case NEXT_EPOCH_DATA -> {
                List<Authority> authorities = reader.read(new ListReader<>(AuthorityReader.getInstance()));
                byte[] randomness = reader.readUint256();
                babeConsensusMessage.setNextEpochData(new EpochData(authorities, randomness));
            }

            case DISABLED_AUTHORITY ->
                    babeConsensusMessage.setDisabledAuthority(BigInteger.valueOf(reader.readUint32()));

            case NEXT_EPOCH_DESCRIPTOR -> {
                Pair<BigInteger, BigInteger> constant = new PairReader<>(new UInt64Reader(), new UInt64Reader())
                        .read(reader);

                BabeEpoch.BabeAllowedSlots secondarySlot = new EnumReader<>(BabeEpoch.BabeAllowedSlots.values())
                        .read(reader);

                babeConsensusMessage.setNextEpochDescriptor(new EpochDescriptor(constant, secondarySlot));
            }
        }

        return babeConsensusMessage;
    }
}
