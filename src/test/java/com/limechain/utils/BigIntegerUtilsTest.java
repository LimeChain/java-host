package com.limechain.utils;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BigIntegerUtilsTest {
    @Test
    void testExactDivision() {
        BigInteger numerator = new BigInteger("10");
        BigInteger denominator = new BigInteger("5");
        BigInteger result = BigIntegerUtils.divideAndRoundUp(numerator, denominator);
        assertEquals(new BigInteger("2"), result);
    }

    @Test
    void testDivisionWithRemainder() {
        BigInteger numerator = new BigInteger("10");
        BigInteger denominator = new BigInteger("3");
        BigInteger result = BigIntegerUtils.divideAndRoundUp(numerator, denominator);
        assertEquals(new BigInteger("4"), result);
    }

    @Test
    void testDivisionWithOneAsDenominator() {
        BigInteger numerator = new BigInteger("10");
        BigInteger denominator = BigInteger.ONE;
        BigInteger result = BigIntegerUtils.divideAndRoundUp(numerator, denominator);
        assertEquals(numerator, result);
    }

    @Test
    void testDivisionByLargerNumerator() {
        BigInteger numerator = new BigInteger("3");
        BigInteger denominator = new BigInteger("10");
        BigInteger result = BigIntegerUtils.divideAndRoundUp(numerator, denominator);
        assertEquals(BigInteger.ONE, result);
    }

    @Test
    void testZeroNumerator() {
        BigInteger numerator = BigInteger.ZERO;
        BigInteger denominator = new BigInteger("10");
        BigInteger result = BigIntegerUtils.divideAndRoundUp(numerator, denominator);
        assertEquals(BigInteger.ZERO, result);
    }

    @Test
    void testDivisionByZeroDenominator() {
        BigInteger numerator = new BigInteger("10");
        BigInteger denominator = BigInteger.ZERO;
        assertThrows(ArithmeticException.class, () -> BigIntegerUtils.divideAndRoundUp(numerator, denominator));
    }
}
