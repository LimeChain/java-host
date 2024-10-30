package com.limechain.utils;

import java.math.BigInteger;

public class BigIntegerUtils {
    public static BigInteger divideAndRoundUp(BigInteger numerator, BigInteger denominator) {
        BigInteger[] resultReminderTuple = numerator.divideAndRemainder(denominator);
        var result = resultReminderTuple[0];
        var remainder = resultReminderTuple[1];

        if (!remainder.equals(BigInteger.ZERO)) {
            return result.add(BigInteger.ONE);
        }

        return result;
    }
}