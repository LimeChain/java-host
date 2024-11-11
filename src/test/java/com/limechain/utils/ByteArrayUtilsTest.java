package com.limechain.utils;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByteArrayUtilsTest {

    private static final byte[] ELEMENT_1 = "element1".getBytes();
    private static final byte[] ELEMENT_2 = "element2".getBytes();
    private static final byte[] ELEMENT_3 = "element3".getBytes();


    @Test
    void testTargetFound() {
        // Test with both arrays non-null and target found
        byte[] array = {1, 2, 3, 4, 5};
        byte[] target = {3, 4};
        assertEquals(2, ByteArrayUtils.indexOf(array, target));
    }

    @Test
    void testTargetNotFound() {
        // Test with both arrays non-null and target not found
        byte[] array = {1, 2, 3, 4, 5};
        byte[] target = {6, 7};
        assertEquals(-1, ByteArrayUtils.indexOf(array, target));
    }

    @Test
    void testEmptyTarget() {
        // Test with target array empty
        byte[] array = {1, 2, 3, 4, 5};
        byte[] target = {};
        assertEquals(0, ByteArrayUtils.indexOf(array, target));
    }

    @Test
    void testArrayNull() {
        // Test with array null
        byte[] target = {1, 2};
        assertEquals(-1, ByteArrayUtils.indexOf(null, target));
    }

    @Test
    void testTargetNull() {
        // Test with target null
        byte[] array = {1, 2, 3, 4, 5};
        assertEquals(-1, ByteArrayUtils.indexOf(array, null));
    }

    @Test
    void testBothNull() {
        // Test with both arrays null
        assertEquals(-1, ByteArrayUtils.indexOf(null, null));
    }

    @Test
    void testSourceContainsAll_AllTargetElementsInSource() {
        Collection<byte[]> source = List.of(
                ELEMENT_1,
                ELEMENT_2,
                ELEMENT_3
        );

        Collection<byte[]> target = List.of(
                ELEMENT_1,
                ELEMENT_2
        );

        boolean result = ByteArrayUtils.sourceContainsAll(source, target);
        assertTrue(result);
    }

    @Test
    void testSourceContainsAll_SourceHasExtraElements() {
        Collection<byte[]> source = List.of(
                ELEMENT_1,
                ELEMENT_2,
                ELEMENT_3,
                "extraElement".getBytes()
        );

        Collection<byte[]> target = List.of(
                ELEMENT_1,
                ELEMENT_2
        );

        boolean result = ByteArrayUtils.sourceContainsAll(source, target);
        assertTrue(result);
    }

    @Test
    void testSourceContainsAll_MissingElementInSource() {
        Collection<byte[]> source = List.of(
                ELEMENT_1,
                ELEMENT_2
        );

        Collection<byte[]> target = List.of(
                ELEMENT_1,
                ELEMENT_2,
                "missingElement".getBytes()
        );

        boolean result = ByteArrayUtils.sourceContainsAll(source, target);
        assertFalse(result);
    }

    @Test
    void testSourceContainsAll_EmptySource() {
        Collection<byte[]> source = Collections.emptyList();

        Collection<byte[]> target = List.of(
                ELEMENT_1
        );

        boolean result = ByteArrayUtils.sourceContainsAll(source, target);
        assertFalse(result);
    }

    @Test
    void testSourceContainsAll_EmptyTarget() {
        Collection<byte[]> source = List.of(
                ELEMENT_1,
                ELEMENT_2
        );
        Collection<byte[]> target = Collections.emptyList();

        boolean result = ByteArrayUtils.sourceContainsAll(source, target);
        assertTrue(result);
    }

    @Test
    void testSourceContainsAll_BothEmpty() {
        Collection<byte[]> source = Collections.emptyList();
        Collection<byte[]> target = Collections.emptyList();
        boolean result = ByteArrayUtils.sourceContainsAll(source, target);
        assertTrue(result);  // Both empty collections should result in true
    }
}