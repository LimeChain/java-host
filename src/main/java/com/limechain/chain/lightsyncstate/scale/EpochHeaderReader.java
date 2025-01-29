package com.limechain.chain.lightsyncstate.scale;

import com.limechain.chain.lightsyncstate.EpochHeader;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EpochHeaderReader implements ScaleReader<EpochHeader> {

    private static final EpochHeaderReader INSTANCE = new EpochHeaderReader();

    public static EpochHeaderReader getInstance() {
        return INSTANCE;
    }

    @Override
    public EpochHeader read(ScaleCodecReader reader) {
        EpochHeader header = new EpochHeader();
        header.setStartSlot(new UInt64Reader().read(reader));
        header.setEndSlot(new UInt64Reader().read(reader));
        return header;
    }
}
