package com.limechain.trie.structure;

import lombok.Data;

//TODO:
// Consider making this data class an inner class of TrieStructure to guarantee
// that the index has been generated by the same TrieStructure instance (with a runtime check, ofc)

/**
 * A semantic wrapper for an integer, representing a node index within our trie structure.
 * Cannot be instantiated outside, so only the {@link TrieStructure} can generate them and provides them to the user
 * as an opaque index for nodes.
 */
@Data
public final class TrieNodeIndex {
    private final int value;

    TrieNodeIndex(int value) {
        this.value = value;
    }
}
