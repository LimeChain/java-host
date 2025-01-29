package com.limechain.chain.lightsyncstate.scale;

import com.limechain.chain.lightsyncstate.BabeEpoch;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.javatuples.Pair;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BabeConfigReader implements ScaleReader<BabeEpoch.NextBabeConfig> {

    private static final BabeConfigReader INSTANCE = new BabeConfigReader();

    public static BabeConfigReader getInstance() {
        return INSTANCE;
    }

    @Override
    public BabeEpoch.NextBabeConfig read(ScaleCodecReader reader) {
        BabeEpoch.NextBabeConfig config = new BabeEpoch.NextBabeConfig();
        config.setC(new Pair<>(new UInt64Reader().read(reader), new UInt64Reader().read(reader)));
        config.setAllowedSlots(BabeEpoch.BabeAllowedSlots.fromId(reader.readUByte()));
        return config;
    }
}
