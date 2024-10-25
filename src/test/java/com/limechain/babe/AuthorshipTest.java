package com.limechain.babe;

import com.limechain.chain.lightsyncstate.Authority;
import org.javatuples.Pair;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorshipTest {

    @Test
    void testCalculatePrimaryThreshold() {
        var constant = new Pair<>(BigInteger.ONE, BigInteger.valueOf(4));
        Authority authority1 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority2 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority3 = new Authority(new byte[32], BigInteger.ONE);

        var authorities = List.of(authority1, authority2, authority3);
        var result = Authorship.calculatePrimaryThreshold(constant, authorities, 0);
        assertEquals(new BigInteger("31115318766088776340791719032032067584"), result);
    }


    @Test
    void testCalculatePrimaryThresholdWithConstantNumeratorEqualsZero() {
        var constant = new Pair<>(BigInteger.ZERO, BigInteger.valueOf(4));
        Authority authority1 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority2 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority3 = new Authority(new byte[32], BigInteger.ONE);

        var authorities = List.of(authority1, authority2, authority3);
        var result = Authorship.calculatePrimaryThreshold(constant, authorities, 0);
        assertEquals(new BigInteger("0"), result);
    }

    @Test
    void testCalculatePrimaryThresholdWithConstantDenominatorEqualsZero() {
        var constant = new Pair<>(BigInteger.ONE, BigInteger.ZERO);
        Authority authority1 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority2 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority3 = new Authority(new byte[32], BigInteger.ONE);

        var authorities = List.of(authority1, authority2, authority3);
        assertThrows(IllegalArgumentException.class,
                () -> Authorship.calculatePrimaryThreshold(constant, authorities, 0));
    }

    @Test
    void testCalculatePrimaryThresholdWithBabeConstantOutOfRange() {
        var constant = new Pair<>(BigInteger.TEN, BigInteger.ONE);
        Authority authority1 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority2 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority3 = new Authority(new byte[32], BigInteger.ONE);

        var authorities = List.of(authority1, authority2, authority3);
        assertThrows(IllegalStateException.class,
                () -> Authorship.calculatePrimaryThreshold(constant, authorities, 0));
    }

    @Test
    void testCalculatePrimaryThresholdWithAuthorityIndexOutOfBounds() {
        var constant = new Pair<>(BigInteger.ONE, BigInteger.valueOf(4));
        Authority authority1 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority2 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority3 = new Authority(new byte[32], BigInteger.ONE);

        var authorities = List.of(authority1, authority2, authority3);
        var nonExistingIndex = Integer.MAX_VALUE;
        assertThrows(IllegalArgumentException.class,
                () -> Authorship.calculatePrimaryThreshold(constant, authorities, nonExistingIndex));
    }

    @Test
    void testCalculatePrimaryThresholdWithNegativeAuthorityIndex() {
        var constant = new Pair<>(BigInteger.ONE, BigInteger.valueOf(4));
        Authority authority1 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority2 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority3 = new Authority(new byte[32], BigInteger.ONE);

        var authorities = List.of(authority1, authority2, authority3);
        assertThrows(IllegalArgumentException.class,
                () -> Authorship.calculatePrimaryThreshold(constant, authorities, -1));
    }
}
