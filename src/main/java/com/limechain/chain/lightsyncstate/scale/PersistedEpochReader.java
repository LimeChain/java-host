package com.limechain.chain.lightsyncstate.scale;

import com.limechain.chain.lightsyncstate.BabeEpoch;
import com.limechain.chain.lightsyncstate.PersistedEpoch;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PersistedEpochReader implements ScaleReader<PersistedEpoch> {

    private static final PersistedEpochReader INSTANCE = new PersistedEpochReader();

    public static PersistedEpochReader getInstance() {
        return INSTANCE;
    }

    @Override
    public PersistedEpoch read(ScaleCodecReader reader) {
        PersistedEpoch epoch = new PersistedEpoch();
        var enumOrdinal = reader.readUByte();

        BabeEpochReader babeEpochReader = BabeEpochReader.getInstance();
        List<BabeEpoch> epochs = new ArrayList<>();
        switch (enumOrdinal) {
            case 0 -> {
                epochs.add(reader.read(babeEpochReader));
                epochs.add(reader.read(babeEpochReader));
            }
            case 1 -> epochs.add(reader.read(babeEpochReader));
            default -> throw new IllegalStateException("Unexpected value: " + enumOrdinal);
        }
        epoch.setBabeEpochs(epochs.toArray(BabeEpoch[]::new));
        return epoch;
    }
}
