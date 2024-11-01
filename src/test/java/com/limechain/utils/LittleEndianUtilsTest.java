package com.limechain.utils;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LittleEndianUtilsTest {
    @Test
    void testBytesToFixedLength() {
        byte[] bigEndianArray = {0x01, 0x02, 0x03, 0x04};
        int targetLength = 4;

        byte[] expectedLittleEndianArray = {0x04, 0x03, 0x02, 0x01};
        byte[] actualLittleEndianArray = LittleEndianUtils.bytesToFixedLength(bigEndianArray, targetLength);
        assertArrayEquals(expectedLittleEndianArray, actualLittleEndianArray);
    }

    @Test
    void testBytesToFixedLengthWithPadding() {
        byte[] bigEndianArray = {0x0A, 0x0B, 0x0C};
        int targetLength = 5;

        byte[] expectedLittleEndianArray = {0x0C, 0x0B, 0x0A, 0x00, 0x00};
        byte[] actualLittleEndianArray = LittleEndianUtils.bytesToFixedLength(bigEndianArray, targetLength);
        assertArrayEquals(expectedLittleEndianArray, actualLittleEndianArray);
    }

    @Test
    void testBytesToFixedLengthWithTruncation() {
        byte[] bigEndianArray = {0x10, 0x20, 0x30, 0x40, 0x50, 0x60};
        int targetLength = 4;

        byte[] expectedLittleEndianArray = {0x60, 0x50, 0x40, 0x30};
        byte[] actualLittleEndianArray = LittleEndianUtils.bytesToFixedLength(bigEndianArray, targetLength);
        assertArrayEquals(expectedLittleEndianArray, actualLittleEndianArray);
    }

    @Test
    void testToLittleEndianBytesWithMaxLongValue() {
        long value = Long.MAX_VALUE;
        byte[] expected = new byte[]{-1, -1, -1, -1, -1, -1, -1, 127}; // Expected little-endian bytes for Long.MAX_VALUE
        byte[] result = LittleEndianUtils.toLittleEndianBytes(BigInteger.valueOf(value));
        assertArrayEquals(expected, result);
    }

    @Test
    void testToLittleEndianBytesWithMinLongValue() {
        long value = Long.MIN_VALUE;
        byte[] expected = new byte[]{0, 0, 0, 0, 0, 0, 0, -128}; // Expected little-endian bytes for Long.MIN_VALUE
        byte[] result = LittleEndianUtils.toLittleEndianBytes(BigInteger.valueOf(value));
        assertArrayEquals(expected, result);
    }

    @Test
    void testToLittleEndianBytesWithZero() {
        byte[] expected = new byte[]{0, 0, 0, 0, 0, 0, 0, 0}; // Expected little-endian bytes for 0
        byte[] result = LittleEndianUtils.toLittleEndianBytes(BigInteger.ZERO);
        assertArrayEquals(expected, result);
    }

    @Test
    void testFromLittleEndianByteArray() {
        byte[] bytes = {1, 0, 0, 0};
        BigInteger expected = BigInteger.ONE;
        BigInteger result = LittleEndianUtils.fromLittleEndianByteArray(bytes);
        assertEquals(expected, result);
    }

    @Test
    void testFromLittleEndianByteArrayWithRandomNumber() {
        // Tested with rust build-in 'u128::from_le_bytes' function
        // let byte_arr: [i8 ; 16] = [-124, -72, -92, 56, 112, -102, 29, 107, -111, -17, 73, -30, -49, -30, 58, 111];
        // let byte_arr = byte_arr.map(|byte| byte as u8);
        // let result = u128::from_le_bytes(byte_arr))
        byte[] bytes = {-124, -72, -92, 56, 112, -102, 29, 107, -111, -17, 73, -30, -49, -30, 58, 111};
        String expected = "147850061044753742757552691558406731908";
        BigInteger result = LittleEndianUtils.fromLittleEndianByteArray(bytes);
        assertEquals(expected, result.toString());
    }

    @Test
    void testFromLittleEndianByteArrayWithEmptyArray() {
        byte[] bytes = {};
        BigInteger expected = BigInteger.ZERO;
        BigInteger result = LittleEndianUtils.fromLittleEndianByteArray(bytes);
        assertEquals(expected, result);
    }
}
