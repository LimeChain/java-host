package com.limechain.babe.api.scale;

import com.limechain.babe.api.BabeApiConfiguration;
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

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BabeApiConfigurationReader implements ScaleReader<BabeApiConfiguration> {

    private static final BabeApiConfigurationReader INSTANCE = new BabeApiConfigurationReader();

    public static BabeApiConfigurationReader getInstance() {
        return INSTANCE;
    }

    @Override
    public BabeApiConfiguration read(ScaleCodecReader reader) {
        BabeApiConfiguration babeApiConfiguration = new BabeApiConfiguration();
        babeApiConfiguration.setSlotDuration(new UInt64Reader().read(reader));
        babeApiConfiguration.setEpochLength(new UInt64Reader().read(reader));
        babeApiConfiguration.setConstant(new PairReader<>(new UInt64Reader(), new UInt64Reader()).read(reader));
        babeApiConfiguration.setAuthorities(reader.read(new ListReader<>(AuthorityReader.getInstance())));
        babeApiConfiguration.setRandomness(reader.readUint256());
        babeApiConfiguration.setAllowedSlots(new EnumReader<>(BabeEpoch.BabeAllowedSlots.values()).read(reader));
        return babeApiConfiguration;
    }
}
