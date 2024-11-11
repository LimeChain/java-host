package com.limechain.trie.cache.node;

import com.limechain.runtime.version.StateVersion;
import com.limechain.trie.structure.nibble.Nibbles;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record PendingInsertUpdate(byte[] newMerkleValue,
                                  List<byte[]> childrenMerkleValues,
                                  Nibbles partialKey,
                                  StateVersion stateVersion,
                                  @Nullable byte[] value) implements PendingTrieNodeChange {

    public PendingInsertUpdate(PendingInsertUpdate original) {
        this(
                original.newMerkleValue != null ? original.newMerkleValue.clone() : null,
                deepCopyChildrenMerkleValues(original.childrenMerkleValues),
                original.partialKey.copy(),
                original.stateVersion,
                original.value != null ? original.value.clone() : null
        );
    }

    private static List<byte[]> deepCopyChildrenMerkleValues(List<byte[]> original) {
        if (original == null) {
            return List.of();
        }

        List<byte[]> copy = new ArrayList<>(original.size());
        for (byte[] child : original) {
            copy.add(child != null ? child.clone() : null);
        }

        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PendingInsertUpdate that = (PendingInsertUpdate) o;
        return Objects.equals(partialKey, that.partialKey)
                && Objects.deepEquals(newMerkleValue, that.newMerkleValue)
                && Objects.equals(childrenMerkleValues, that.childrenMerkleValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(newMerkleValue), partialKey, childrenMerkleValues);
    }

    @Override
    public String toString() {
        return "PendingInsertUpdate{" +
                "newMerkleValue=" + Arrays.toString(newMerkleValue) +
                ", partialKey=" + partialKey +
                ", childrenMerkleValues=" + childrenMerkleValues +
                '}';
    }
}
