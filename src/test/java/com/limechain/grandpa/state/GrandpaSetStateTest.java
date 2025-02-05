package com.limechain.grandpa.state;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.grandpa.round.RoundCache;
import com.limechain.utils.Ed25519Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class GrandpaSetStateTest {

    @Mock
    private RoundCache roundCache;

    @InjectMocks
    private GrandpaSetState grandpaSetState;

    @Test
    void testGetThreshold() {
        Authority authority1 = new Authority(Ed25519Utils.generateKeyPair().publicKey().bytes(), BigInteger.ONE);
        Authority authority2 = new Authority(Ed25519Utils.generateKeyPair().publicKey().bytes(), BigInteger.ONE);
        Authority authority3 = new Authority(Ed25519Utils.generateKeyPair().publicKey().bytes(), BigInteger.ONE);
        Authority authority4 = new Authority(Ed25519Utils.generateKeyPair().publicKey().bytes(), BigInteger.ONE);
        Authority authority5 = new Authority(Ed25519Utils.generateKeyPair().publicKey().bytes(), BigInteger.ONE);
        Authority authority6 = new Authority(Ed25519Utils.generateKeyPair().publicKey().bytes(), BigInteger.ONE);
        Authority authority7 = new Authority(Ed25519Utils.generateKeyPair().publicKey().bytes(), BigInteger.ONE);
        Authority authority8 = new Authority(Ed25519Utils.generateKeyPair().publicKey().bytes(), BigInteger.ONE);
        Authority authority9 = new Authority(Ed25519Utils.generateKeyPair().publicKey().bytes(), BigInteger.ONE);
        Authority authority10 = new Authority(Ed25519Utils.generateKeyPair().publicKey().bytes(), BigInteger.ONE);

        grandpaSetState.setAuthorities(
                List.of(
                        authority1, authority2, authority3, authority4, authority5,
                        authority6, authority7, authority8, authority9, authority10
                )
        );

        // Total weight: 10
        // Faulty: (10 - 1) / 3 = 3
        // Threshold: 10 - faulty = 7
        assertEquals(BigInteger.valueOf(7), grandpaSetState.getThreshold());
    }

    @Test
    void testDerivePrimary() {
        Authority authority1 = new Authority(Ed25519Utils.generateKeyPair().publicKey().bytes(), BigInteger.ONE);
        Authority authority2 = new Authority(Ed25519Utils.generateKeyPair().publicKey().bytes(), BigInteger.ONE);
        Authority authority3 = new Authority(Ed25519Utils.generateKeyPair().publicKey().bytes(), BigInteger.ONE);

        grandpaSetState.setAuthorities(List.of(
                authority1, authority2, authority3
        ));

        grandpaSetState.setSetId(BigInteger.ONE);

        // 4 % voters.size = 1
        assertEquals(BigInteger.ONE, grandpaSetState.derivePrimary(BigInteger.valueOf(4)));

        // 5 % voters.size = 2
        assertEquals(BigInteger.TWO, grandpaSetState.derivePrimary(BigInteger.valueOf(5)));

        // 6 % voters.size = 0
        assertEquals(BigInteger.ZERO, grandpaSetState.derivePrimary(BigInteger.valueOf(6)));
    }
}
