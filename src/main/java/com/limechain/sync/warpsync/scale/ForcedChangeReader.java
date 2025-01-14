package com.limechain.sync.warpsync.scale;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.chain.lightsyncstate.scale.AuthorityReader;
import com.limechain.sync.warpsync.dto.AuthoritySetChange;
import com.limechain.sync.warpsync.dto.ForcedAuthoritySetChange;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.ListReader;

import java.math.BigInteger;

public class ForcedChangeReader implements ScaleReader<AuthoritySetChange> {
    @Override
    public AuthoritySetChange read(ScaleCodecReader reader) {
        BigInteger m = BigInteger.valueOf(reader.readUint32());
        Authority[] authoritiesChanges =
                reader.read(new ListReader<>(new AuthorityReader())).toArray(Authority[]::new);
        BigInteger delay = BigInteger.valueOf(reader.readUint32());
        return new ForcedAuthoritySetChange(authoritiesChanges, delay.add(m));
    }
}
