package com.limechain.babe.api.scale;

import com.limechain.babe.state.CurrentEpoch;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;

// Note: runtime call returns also epoch length, authority list and randomness
public class CurrentEpochReader implements ScaleReader<CurrentEpoch> {

    @Override
    public CurrentEpoch read(ScaleCodecReader reader) {
        return new CurrentEpoch(new UInt64Reader().read(reader), new UInt64Reader().read(reader));
    }
}