package com.limechain.chain.lightsyncstate.scale;

import com.limechain.chain.lightsyncstate.PendingChange;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.ListReader;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PendingChangeReader implements ScaleReader<PendingChange> {

    private static final PendingChangeReader INSTANCE = new PendingChangeReader();

    public static PendingChangeReader getInstance() {
        return INSTANCE;
    }

    @Override
    public PendingChange read(ScaleCodecReader reader) {
        PendingChange pendingChange = new PendingChange();
        pendingChange.setNextAuthorities(reader.read(new ListReader<>(AuthorityReader.getInstance())));
        pendingChange.setDelay(BigInteger.valueOf(reader.readUint32()));
        pendingChange.setCanonHeight(BigInteger.valueOf(reader.readUint32()));
        pendingChange.setCanonHash(new Hash256(reader.readUint256()));
        pendingChange.setDelayKind(DelayKindReader.getInstance().read(reader));
        return pendingChange;
    }
}
