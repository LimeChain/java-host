package com.limechain.utils;

import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Collection;

@UtilityClass
public class ByteArrayUtils {

    /**
     * Calculates the length of the common prefix between two byte arrays.
     * <p>
     * This method iterates over the arrays and compares them byte by byte to find
     * the length of the common prefix, i.e., the number of leading bytes that are identical
     * in both arrays.
     *
     * @param a The first byte array.
     * @param b The second byte array.
     * @return The length of the common prefix. Returns 0 if the first byte is not the same or
     * if either array is empty.
     */
    public static int commonPrefixLength(byte[] a, byte[] b) {
        int minLength = Math.min(a.length, b.length);
        if (minLength == 0) {
            return 0;
        }
        int length;
        for (length = 0; length < minLength; length++) {
            if (a[length] != b[length]) {
                break;
            }
        }
        return length;
    }

    /**
     * Checks if the given array starts with the specified prefix.
     * <p>
     * This method compares the prefix array to the beginning of the main array to determine
     * if the main array starts with the given prefix.
     *
     * @param array  The byte array to check.
     * @param prefix The byte array representing the prefix to look for.
     * @return {@code true} if the array starts with the prefix; {@code false} otherwise.
     * Also returns {@code false} if the main array is shorter than the prefix.
     */
    public static boolean hasPrefix(byte[] array, byte[] prefix) {
        if (array.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (array[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the start position of the first occurrence of the specified {@code target} within
     * {@code array}, or {@code -1} if there is no such occurrence.
     *
     * <p>More formally, returns the lowest index {@code i} such that {@code Arrays.copyOfRange(array,
     * i, i + target.length)} contains exactly the same elements as {@code target}.
     *
     * @param array  the array to search for the sequence {@code target}
     * @param target the array to search for as a sub-sequence of {@code array}
     */
    public static int indexOf(byte[] array, byte[] target) {
        if (array == null || target == null) {
            return -1;
        }
        if (target.length == 0) {
            return 0;
        }

        for (int i = 0; i < array.length - target.length + 1; i++) {
            boolean shouldReturn = true;
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    shouldReturn = false;
                    break;
                }
            }
            if (shouldReturn) return i;
        }
        return -1;
    }

    /**
     * Concatenates two byte arrays into a single byte array. This method combines the contents
     * of the first byte array and the second byte array into a new byte array, where the
     * contents of the first array precede those of the second.
     *
     * @param prefix The first byte array to concatenate.
     * @param suffix The second byte array to concatenate.
     * @return A new byte array containing the concatenated result of the two input byte arrays.
     */
    public static byte[] concatenate(byte[] prefix, byte[] suffix) {
        byte[] result = new byte[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(suffix, 0, result, prefix.length, suffix.length);
        return result;
    }

    /**
     * Checks if all the byte arrays in the target collection are present in the source collection.
     *
     * @param source the collection of byte arrays that may contain the target arrays
     * @param target the collection of byte arrays to check for presence in the source
     * @return {@code true} if all byte arrays in the target collection are present in the source
     * collection, {@code false} otherwise
     */
    public boolean sourceContainsAll(Collection<byte[]> source, Collection<byte[]> target) {
        return target.stream()
                .allMatch(targetArray ->
                        source.stream()
                                .anyMatch(sourceArray -> Arrays.equals(sourceArray, targetArray))
                );
    }
}
