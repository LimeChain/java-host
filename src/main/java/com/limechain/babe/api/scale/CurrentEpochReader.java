package com.limechain.babe.api.scale;

import com.limechain.babe.state.CurrentEpoch;
import com.limechain.chain.lightsyncstate.scale.AuthorityReader;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.ListReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;

public class CurrentEpochReader implements ScaleReader<CurrentEpoch> {

    @Override
    public CurrentEpoch read(ScaleCodecReader reader) {
        return CurrentEpoch.builder()
                .epochIndex(new UInt64Reader().read(reader))
                .epochStartingSlot(new UInt64Reader().read(reader))
                .epochLength(new UInt64Reader().read(reader))
                .authorityList(reader.read(new ListReader<>(new AuthorityReader())))
                .randomness(reader.readUint256())
                .build();
    }
}