package com.limechain.chain.lightsyncstate.scale;

import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.javatuples.Pair;

import java.math.BigInteger;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AuthoritySetChangeReader implements ScaleReader<Pair<BigInteger, BigInteger>> {

    private static final AuthoritySetChangeReader INSTANCE = new AuthoritySetChangeReader();

    public static AuthoritySetChangeReader getInstance() {
        return INSTANCE;
    }

    @Override
    public Pair<BigInteger, BigInteger> read(ScaleCodecReader reader) {
        return new Pair<>(
                new UInt64Reader().read(reader),
                BigInteger.valueOf(reader.readUint32())
        );
    }
}
