package com.limechain.chain.lightsyncstate.scale;

import com.limechain.chain.lightsyncstate.PendingChange;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.UInt32Reader;

import java.math.BigInteger;

public class DelayKindReader implements ScaleReader<PendingChange.DelayKind> {
    @Override
    public PendingChange.DelayKind read(ScaleCodecReader reader) {
        var enumOrdinal = reader.readUByte();
        var delayKind = new PendingChange.DelayKind();
        switch (enumOrdinal) {
            case 0 -> {
                delayKind.setKind(PendingChange.DelayKindEnum.FINALIZED);
                return delayKind;
            }
            case 1 -> {
                delayKind.setKind(PendingChange.DelayKindEnum.BEST);
                delayKind.setMedianLastFinalized(BigInteger.valueOf(new UInt32Reader().read(reader)));
                return delayKind;
            }
            default -> throw new IllegalStateException("Unexpected value: " + enumOrdinal);
        }
    }

}