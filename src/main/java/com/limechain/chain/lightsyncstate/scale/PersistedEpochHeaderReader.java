package com.limechain.chain.lightsyncstate.scale;

import com.limechain.chain.lightsyncstate.EpochHeader;
import com.limechain.chain.lightsyncstate.PersistedEpochHeader;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PersistedEpochHeaderReader implements ScaleReader<PersistedEpochHeader> {

    private static final PersistedEpochHeaderReader INSTANCE = new PersistedEpochHeaderReader();

    public static PersistedEpochHeaderReader getInstance() {
        return INSTANCE;
    }

    @Override
    public PersistedEpochHeader read(ScaleCodecReader reader) {
        PersistedEpochHeader header = new PersistedEpochHeader();
        var enumOrdinal = reader.readUByte();

        EpochHeaderReader epochHeaderReader = EpochHeaderReader.getInstance();
        List<EpochHeader> headers = new ArrayList<>();
        switch (enumOrdinal) {
            case 0 -> {
                headers.add(reader.read(epochHeaderReader));
                headers.add(reader.read(epochHeaderReader));
            }
            case 1 -> headers.add(reader.read(epochHeaderReader));
            default -> throw new IllegalStateException("Unexpected value: " + enumOrdinal);
        }
        header.setBabeEpochs(headers.toArray(EpochHeader[]::new));
        return header;
    }
}
