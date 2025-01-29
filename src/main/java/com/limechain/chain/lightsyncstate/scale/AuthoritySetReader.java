package com.limechain.chain.lightsyncstate.scale;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.chain.lightsyncstate.AuthoritySet;
import com.limechain.chain.lightsyncstate.ForkTree;
import com.limechain.chain.lightsyncstate.PendingChange;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.ListReader;
import io.emeraldpay.polkaj.scale.reader.UInt32Reader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.javatuples.Pair;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AuthoritySetReader implements ScaleReader<AuthoritySet> {

    private static final AuthoritySetReader INSTANCE = new AuthoritySetReader();

    public static AuthoritySetReader getInstance() {
        return INSTANCE;
    }

    @Override
    public AuthoritySet read(ScaleCodecReader reader) {
        AuthoritySet authoritySet = new AuthoritySet();

        authoritySet.setCurrentAuthorities(
                reader.read(new ListReader<>(AuthorityReader.getInstance())).toArray(Authority[]::new)
        );

        authoritySet.setSetId(new UInt64Reader().read(reader));

        var forkTree = new ForkTree<>();
        forkTree.setRoots(reader.read(new ListReader<>(
                new ForkTreeNodeReader<>(PendingChangeReader.getInstance()))
        ).toArray(ForkTree.ForkTreeNode[]::new));
        forkTree.setBestFinalizedNumber(reader.readOptional(new UInt32Reader()));

        authoritySet.setPendingForcedChanges(reader.read(
                new ListReader<>(PendingChangeReader.getInstance())).toArray(PendingChange[]::new)
        );

        authoritySet.setAuthoritySetChanges(
                reader.read(new ListReader<>(AuthoritySetChangeReader.getInstance())).toArray(Pair[]::new)
        );

        return authoritySet;
    }
}
