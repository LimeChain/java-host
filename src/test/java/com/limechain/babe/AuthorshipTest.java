package com.limechain.babe;

import com.limechain.chain.lightsyncstate.Authority;
import org.javatuples.Pair;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorshipTest {


    @Test
    void testCalculatePrimaryThreshold()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        var constant = new Pair<>(BigInteger.ONE, BigInteger.valueOf(4));
        Authority authority1 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority2 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority3 = new Authority(new byte[32], BigInteger.ONE);

        var authorities = List.of(authority1, authority2, authority3);
        var result = calculatePrimaryThreshold(constant, authorities, 0);
        assertEquals(new BigInteger("31115318766088776340791719032032067584"), result);
    }

    @Test
    void testCalculatePrimaryThresholdWithConstantNumeratorEqualsZero()
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        var constant = new Pair<>(BigInteger.ZERO, BigInteger.valueOf(4));
        Authority authority1 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority2 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority3 = new Authority(new byte[32], BigInteger.ONE);

        var authorities = List.of(authority1, authority2, authority3);
        var result = calculatePrimaryThreshold(constant, authorities, 0);
        assertEquals(new BigInteger("0"), result);
    }

    @Test
    void testCalculatePrimaryThresholdWithConstantDenominatorEqualsZero() {
        var constant = new Pair<>(BigInteger.ONE, BigInteger.ZERO);
        Authority authority1 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority2 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority3 = new Authority(new byte[32], BigInteger.ONE);

        var authorities = List.of(authority1, authority2, authority3);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> calculatePrimaryThreshold(constant, authorities, 0));

        assertInstanceOf(IllegalArgumentException.class, thrown.getTargetException());
    }

    @Test
    void testCalculatePrimaryThresholdWithBabeConstantOutOfRange() {
        var constant = new Pair<>(BigInteger.TEN, BigInteger.ONE);
        Authority authority1 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority2 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority3 = new Authority(new byte[32], BigInteger.ONE);

        var authorities = List.of(authority1, authority2, authority3);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> calculatePrimaryThreshold(constant, authorities, 0));

        assertInstanceOf(IllegalStateException.class, thrown.getTargetException());
    }

    @Test
    void testCalculatePrimaryThresholdWithAuthorityIndexOutOfBounds() {
        var constant = new Pair<>(BigInteger.ONE, BigInteger.valueOf(4));
        Authority authority1 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority2 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority3 = new Authority(new byte[32], BigInteger.ONE);

        var authorities = List.of(authority1, authority2, authority3);
        var nonExistingIndex = Integer.MAX_VALUE;
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> calculatePrimaryThreshold(constant, authorities, nonExistingIndex));

        assertInstanceOf(IllegalArgumentException.class, thrown.getTargetException());
    }

    @Test
    void testCalculatePrimaryThresholdWithNegativeAuthorityIndex() {
        var constant = new Pair<>(BigInteger.ONE, BigInteger.valueOf(4));
        Authority authority1 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority2 = new Authority(new byte[32], BigInteger.ONE);
        Authority authority3 = new Authority(new byte[32], BigInteger.ONE);

        var authorities = List.of(authority1, authority2, authority3);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                (() -> calculatePrimaryThreshold(constant, authorities, -1)));

        assertInstanceOf(IllegalArgumentException.class, thrown.getTargetException());
    }

    private BigInteger calculatePrimaryThreshold(
            Pair<BigInteger, BigInteger> constant,
            List<Authority> authorities,
            int authorityIndex
    ) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        Method calculatePrimaryThreshold = Authorship.class.getDeclaredMethod(
                "calculatePrimaryThreshold",
                Pair.class,
                List.class,
                int.class
        );
        calculatePrimaryThreshold.setAccessible(true);
        return (BigInteger) calculatePrimaryThreshold.invoke(null, constant, authorities, authorityIndex);
    }
}
