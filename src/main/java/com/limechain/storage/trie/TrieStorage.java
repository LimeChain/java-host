package com.limechain.storage.trie;

import com.google.common.primitives.Bytes;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.runtime.version.StateVersion;
import com.limechain.storage.KVRepository;
import com.limechain.storage.block.BlockState;
import com.limechain.storage.trie.exceptions.ChildNotFoundException;
import com.limechain.trie.structure.TrieStructure;
import com.limechain.trie.structure.nibble.Nibble;
import com.limechain.trie.structure.nibble.Nibbles;
import com.limechain.trie.structure.nibble.NibblesUtils;
import com.limechain.trie.structure.node.InsertTrieNode;
import com.limechain.trie.structure.node.TrieNodeData;
import com.limechain.utils.ByteArrayUtils;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.Getter;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Log
public class TrieStorage {

    private static final String TRIE_NODE_PREFIX = "tn:";
    @Getter
    private static final TrieStorage instance = new TrieStorage();
    private final BlockState blockState = BlockState.getInstance();
    private static final byte[] EMPTY_TRIE_NODE = new byte[0];
    private KVRepository<String, Object> db;

    private TrieStorage() {
    }

    /**
     * Converts a list of nibbles to a byte array. (Have in mind that this byte array is not the byte array from which
     * the nibble is constructed)
     *
     * @param nibbles The list of nibbles to convert.
     * @return A byte array representing the combined nibbles.
     */
    @NotNull
    private static byte[] partialKeyFromNibbles(List<Nibble> nibbles) {
        return Bytes.toArray(
                nibbles.stream()
                        .map(Nibble::asByte)
                        .toList());
    }

    /**
     * Converts a byte array (reverse of @Link partialKeyFromNibbles) to a list of nibbles.
     *
     * @param bytes The byte array to convert.
     * @return A list of nibbles representing the byte array.
     */
    private static Nibbles nibblesFromBytes(byte[] bytes) {
        List<Nibble> nibbles = new ArrayList<>();
        for (byte aByte : bytes) {
            nibbles.add(Nibble.fromByte(aByte));
        }
        return Nibbles.of(nibbles);
    }

    /**
     * Initializes the TrieStorage with a given key-value repository.
     * This method must be called before using the TrieStorage instance.
     *
     * @param db The key-value repository to be used for storing trie nodes.
     */

    public void initialize(final KVRepository<String, Object> db) {
        this.db = db;
    }

    /**
     * Inserts trie node storage data into the key-value repository.
     * <p>
     * The storage data is represented by a {@link TrieNodeData} object, which is serialized and saved to the repository.
     *
     * @param db           The key-value repository where trie node storage is to be saved.
     * @param trieNode     The trie node whose storage data is to be inserted.
     * @param stateVersion The version of the state, affecting how the storage value is interpreted.
     */
    public void insertTrieNodeStorage(KVRepository<String, Object> db, InsertTrieNode trieNode,
                                      StateVersion stateVersion) {
        String key = TRIE_NODE_PREFIX + new String(trieNode.merkleValue());

        List<Nibble> nibbles = trieNode.partialKeyNibbles();
        byte[] partialKey = partialKeyFromNibbles(nibbles);

        List<byte[]> childrenMerkleValues = trieNode.childrenMerkleValues();

        TrieNodeData storageValue = new TrieNodeData(
                partialKey,
                childrenMerkleValues,
                trieNode.isReferenceValue() ? null : trieNode.storageValue(),
                trieNode.isReferenceValue() ? trieNode.storageValue() : null,
                (byte) stateVersion.asInt());

        db.save(key, storageValue);
    }

    /**
     * Retrieves a value by key from the trie associated with a specific block hash.
     *
     * @param blockHash The hash of the block whose trie is to be searched.
     * @param keyStr    The key for which to retrieve the value.
     * @return An {@link Optional} containing the value associated with the key if found, or empty if not found.
     */
    public Optional<byte[]> getByKeyFromBlock(Hash256 blockHash, String keyStr) {
        BlockHeader header = blockState.getHeader(blockHash);

        if (header == null) {
            return Optional.empty();
        }

        TrieNodeData rootNode = getTrieNodeFromMerkleValue(header.getStateRoot().getBytes());
        if (rootNode == null) {
            return Optional.empty();
        }

        byte[] key = partialKeyFromNibbles(Nibbles.fromBytes(keyStr.getBytes()).asUnmodifiableList());
        return Optional.ofNullable(getValueFromDb(rootNode, key));
    }

    /**
     * Recursively searches for a value by key starting from a given trie node.
     * <p>
     *
     * @param trieNode The trie node from which to start the search.
     * @param key      The key for which to search. (should be nibbles array)
     * @return The value associated with the key, or {@code null} if not found.
     * @throws RuntimeException If a referenced child node cannot be found in the database.
     */
    private byte[] getValueFromDb(TrieNodeData trieNode, byte[] key) {
        TrieNodeData nodeFromDb = getNodeFromDb(trieNode, key);
        return nodeFromDb != null ? nodeFromDb.getValue() : null;
    }

    /**
     * Recursively searches for a TrieNode by key starting from a given trie node.
     * <p>
     * This method compares the provided key with the trie node's partial key. If they match,
     * it returns the node's value. Otherwise, it iterates through the node's children,
     * recursively searching for the value in both inline and referenced child nodes.
     * If a child node is referenced by a hash, the method fetches and decodes the child node
     * from the database before continuing the search.
     *
     * @param trieNode The trie node from which to start the search.
     * @param key      The key for which to search. (should be nibbles array)
     * @return The value associated with the key, or {@code null} if not found.
     * @throws ChildNotFoundException If a referenced child node cannot be found in the database.
     */
    private TrieNodeData getNodeFromDb(TrieNodeData trieNode, byte[] key) {
        if (Arrays.equals(trieNode.getPartialKey(), key)) {
            return trieNode;
        }

        int commonPrefix = ByteArrayUtils.commonPrefixLength(trieNode.getPartialKey(), key);
        byte[] childMerkleValue = trieNode.getChildrenMerkleValues().get(key[commonPrefix]);

        if (childMerkleValue == null) {
            return null;
        }
        key = Arrays.copyOfRange(key, 1 + commonPrefix, key.length);

        // Node is referenced by hash, fetch and decode
        TrieNodeData childNode = getTrieNodeFromMerkleValue(childMerkleValue);
        if (childNode == null) {
            throw new ChildNotFoundException(
                    "Child node not found in database for hash: " + Arrays.toString(childMerkleValue));
        }

        return getNodeFromDb(childNode, key);
    }

    private TrieNodeData getTrieNodeFromMerkleValue(byte[] childMerkleValue) {
        Optional<Object> encodedChild = db.find(TRIE_NODE_PREFIX + new String(childMerkleValue));

        return (TrieNodeData) encodedChild.orElse(null);
    }

    /**
     * Finds the next key in the trie that is lexicographically greater than a given prefix.
     * It navigates the trie from the root node associated with a specific block hash.
     *
     * @param blockHash The hash of the block whose trie is used for the search.
     * @param prefixStr The prefix to compare against for finding the next key.
     * @return The next key as a String, or null if no such key exists.
     */
    public String getNextKey(Hash256 blockHash, String prefixStr) {
        BlockHeader header = blockState.getHeader(blockHash);

        if (header == null) {
            return null;
        }

        TrieNodeData rootNode = getTrieNodeFromMerkleValue(header.getStateRoot().getBytes());
        if (rootNode == null) {
            return null;
        }

        byte[] prefix = partialKeyFromNibbles(Nibbles.fromBytes(prefixStr.getBytes()).asUnmodifiableList());

        return findNextKey(rootNode, prefix);
    }

    private String findNextKey(TrieNodeData rootNode, byte[] prefix) {
        byte[] nextKeyBytes = searchForNextKey(rootNode, prefix, new byte[0]);

        // If a next key is found, convert it back to a String and return.
        return nextKeyBytes == EMPTY_TRIE_NODE ? null : NibblesUtils.toStringPrepending(nibblesFromBytes(nextKeyBytes));
    }

    private byte[] searchForNextKey(TrieNodeData node, byte[] prefix, byte[] currentPath) {
        if (node == null) {
            return EMPTY_TRIE_NODE;
        }

        byte[] fullPath = ByteArrayUtils.concatenate(currentPath, node.getPartialKey());
        List<byte[]> childrenMerkleValues = node.getChildrenMerkleValues();

        // If the current node is a leaf and the fullPath is greater than the prefix, it's a candidate.
        if (childrenMerkleValues.stream().allMatch(Objects::isNull) && Arrays.compare(fullPath, prefix) > 0) {
            return fullPath;
        }

        int startIndex = prefix.length > fullPath.length ? prefix[fullPath.length] : 0;

        for (int i = startIndex; i < childrenMerkleValues.size(); i++) {
            byte[] childMerkleValue = childrenMerkleValues.get(i);
            if (childMerkleValue == null) continue; // Skip empty slots.

            // Fetch the child node based on its merkle value.
            TrieNodeData childNode = getTrieNodeFromMerkleValue(childMerkleValue);
            byte[] result = searchForNextKey(childNode, prefix, ByteArrayUtils.concatenate(fullPath, new byte[]{(byte) i}));
            if (result != EMPTY_TRIE_NODE) {
                // If a result is found in this subtree, return it.
                return result;
            }
        }

        return EMPTY_TRIE_NODE;
    }

    public boolean persistAllChanges(TrieStructure<byte[]> trie) {
        return false;
    }

}
