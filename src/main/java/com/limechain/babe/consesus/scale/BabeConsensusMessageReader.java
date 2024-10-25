package com.limechain.babe.consesus.scale;

import com.limechain.babe.consesus.BabeConsensusMessage;
import com.limechain.babe.consesus.BabeConsensusMessageFormat;
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
import org.javatuples.Pair;

import java.math.BigInteger;
import java.util.List;

public class BabeConsensusMessageReader implements ScaleReader<BabeConsensusMessage> {

    @Override
    public BabeConsensusMessage read(ScaleCodecReader reader) {
        BabeConsensusMessage babeConsensusMessage = new BabeConsensusMessage();
        BabeConsensusMessageFormat format = BabeConsensusMessageFormat.fromFormat(reader.readByte());
        babeConsensusMessage.setFormat(format);
        switch (format) {
            case NEXT_EPOCH_DATA -> {
                List<Authority> authorities = reader.read(new ListReader<>(new AuthorityReader()));
                byte[] randomness = reader.readUint256();
                babeConsensusMessage.setNextEpochData(new EpochData(authorities, randomness));
            }
            case DISABLED_AUTHORITY -> babeConsensusMessage.setDisabledAuthority(reader.readUint32());
            case NEXT_EPOCH_DESCRIPTOR -> {
                Pair<BigInteger, BigInteger> constant = new PairReader<>(new UInt64Reader(), new UInt64Reader()).read(reader);
                BabeEpoch.BabeAllowedSlots secondarySlot = new EnumReader<>(BabeEpoch.BabeAllowedSlots.values()).read(reader);
                babeConsensusMessage.setNextEpochDescriptor(new EpochDescriptor(constant, secondarySlot));
            }
        }
        return babeConsensusMessage;
    }
}
