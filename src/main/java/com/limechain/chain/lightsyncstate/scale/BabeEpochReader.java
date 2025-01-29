package com.limechain.chain.lightsyncstate.scale;

import com.limechain.chain.lightsyncstate.BabeEpoch;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.ListReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BabeEpochReader implements ScaleReader<BabeEpoch> {

    private static final BabeEpochReader INSTANCE = new BabeEpochReader();

    public static BabeEpochReader getInstance() {
        return INSTANCE;
    }

    @Override
    public BabeEpoch read(ScaleCodecReader reader) {
        BabeEpoch epoch = new BabeEpoch();
        epoch.setEpochIndex(new UInt64Reader().read(reader));
        epoch.setSlotNumber(new UInt64Reader().read(reader));
        epoch.setDuration(new UInt64Reader().read(reader));
        epoch.setAuthorities(reader.read(new ListReader<>(AuthorityReader.getInstance())));
        epoch.setRandomness(reader.readUint256());
        epoch.setNextConfig(reader.read(BabeConfigReader.getInstance()));
        return epoch;
    }
}
