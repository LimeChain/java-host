package com.limechain.storage.trie;

import com.limechain.runtime.version.StateVersion;
import com.limechain.storage.KVRepository;
import com.limechain.trie.cache.node.PendingInsertUpdate;
import com.limechain.trie.dto.node.StorageNode;
import com.limechain.trie.structure.TrieNodeIndex;
import com.limechain.trie.structure.TrieStructure;
import com.limechain.trie.structure.database.InsertTrieBuilder;
import com.limechain.trie.structure.database.NodeData;
import com.limechain.trie.structure.nibble.Nibble;
import com.limechain.trie.structure.nibble.Nibbles;
import com.limechain.trie.structure.node.InsertTrieNode;
import com.limechain.trie.structure.node.TrieNodeData;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

/**
 * A wrapper around a KVRepository that provides a public interface for trie-related DB queries.
 */
@Log
@AllArgsConstructor
public class TrieStorage {

    private static final String TRIE_NODE_PREFIX = "tn:";
    private KVRepository<String, Object> db;

    /**
     * Retrieves a value by key from the trie associated with a specific block hash.
     *
     * @param merkleRoot The merkle root under which to search.
     * @param key        The key for which to retrieve the value.
     * @return An {@link Optional} containing the value associated with the key if found, or empty if not found.
     */
    public Optional<NodeData> getByKeyFromMerkle(byte[] merkleRoot, Nibbles key) {
        NodeData nodeFromDb = getNodeFromDb(merkleRoot, key);
        if (nodeFromDb != null && nodeFromDb.getMerkleValue() == null) {
            nodeFromDb.setMerkleValue(merkleRoot);
        }
        return Optional.ofNullable(nodeFromDb);
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
     * @param nodeMerkleValue The Merkle value of the trie node from which to start the search.
     * @param key             The key for which to search.
     * @return The node data associated with the key, or {@code null} if not found.
     */
    private NodeData getNodeFromDb(byte[] nodeMerkleValue, Nibbles key) {
        final TrieNodeData trieNode = getTrieNodeFromMerkleValue(nodeMerkleValue);

        if (trieNode == null) {
            return null;
        }

        assert trieNode.getChildrenMerkleValues().size() == 16;

        // If the current node's partial key IS NOT a prefix of the sought key,
        // then we don't have a node for the sought key in the trie
        if (!key.startsWith(trieNode.getPartialKey())) {
            return null;
        }

        var remainder = key.drop(trieNode.getPartialKey().size());

        // If we've found the node we're looking for:
        if (remainder.isEmpty()) {
            return new NodeData(trieNode.getValue(), nodeMerkleValue);
        }

        // Else, we've got more work to do:
        var childIndexWithinParent = remainder.get(0);

        byte[] childMerkleValue = trieNode.getChildrenMerkleValues().get(childIndexWithinParent.asInt());

        if (childMerkleValue == null) {
            return null;
        }

        return getNodeFromDb(childMerkleValue, remainder.drop(1));
    }

    @Nullable
    public TrieNodeData getTrieNodeFromMerkleValue(@NotNull byte[] childMerkleValue) {
        Optional<Object> encodedChild = db.find(TRIE_NODE_PREFIX + new String(childMerkleValue));

        return (TrieNodeData) encodedChild.orElse(null);
    }

    public boolean merkleValueExists(Hash256 lastFinalizedStateRoot) {
        return getTrieNodeFromMerkleValue(lastFinalizedStateRoot.getBytes()) != null;
    }

    /**
     * Finds the next key in the trie that is lexicographically greater than a given prefix.
     * It navigates the trie from the root node associated with a specific block hash.
     *
     * @param merkleValue The merkleValue of the trie to be used for the search.
     * @param prefix      The prefix to compare against for finding the next key.
     * @return The next key as a String, or null if no such key exists.
     */
    @Nullable
    public Nibbles getNextKeyByMerkleValue(byte[] merkleValue, Nibbles prefix) {
        TrieNodeData rootNode = getTrieNodeFromMerkleValue(merkleValue);
        if (rootNode == null) {
            return null;
        }

        return findNextKey(rootNode, prefix);
    }

    private Nibbles findNextKey(TrieNodeData rootNode, Nibbles prefix) {
        return searchForNextKey(rootNode, prefix, rootNode.getPartialKey());
    }

    @Nullable
    private Nibbles searchForNextKey(TrieNodeData node, Nibbles prefix, Nibbles currentPath) {
        if (node == null) {
            return null;
        }

        // If the current node is a leaf and the fullPath is greater than the prefix, it's a candidate.
        if (node.getValue() != null && currentPath.compareTo(prefix) > 0) {
            return currentPath;
        }

        List<byte[]> childrenMerkleValues = node.getChildrenMerkleValues();
        int startIndex = prefix.size() > currentPath.size() ? prefix.get(currentPath.size()).asInt() : 0;

        for (int i = startIndex; i < childrenMerkleValues.size(); i++) {
            byte[] childMerkleValue = childrenMerkleValues.get(i);
            TrieNodeData childNode = null;
            if (childMerkleValue != null) {
                childNode = getTrieNodeFromMerkleValue(childMerkleValue);
            }
            if (childNode == null) continue;

            Nibbles nextPath = currentPath.add(Nibble.fromInt(i)).addAll(childNode.getPartialKey());

            Nibbles result = searchForNextKey(childNode, prefix, nextPath);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Searches for the next branch in the trie that is lexicographically greater than a given prefix.
     *
     * @param blockStateRoot The state root to start the search from.
     * @param prefix         The prefix to search for the next branch.
     * @return An {@link Optional} containing the next {@link StorageNode} branch if found, otherwise an empty {@link Optional}.
     */
    public Optional<StorageNode> getNextBranch(Hash256 blockStateRoot, Nibbles prefix) {
        byte[] rootMerkleValue = blockStateRoot.getBytes();
        TrieNodeData rootNode = getTrieNodeFromMerkleValue(rootMerkleValue);
        if (rootNode == null) {
            return Optional.empty();
        }

        if (prefix.equals(rootNode.getPartialKey())) {
            return Optional.of(new StorageNode(rootNode.getPartialKey(),
                new NodeData(rootNode.getValue(), rootMerkleValue)));
        }

        return Optional.ofNullable(searchForNextBranch(rootNode, prefix, rootNode.getPartialKey()));
    }

    private StorageNode searchForNextBranch(TrieNodeData node, Nibbles prefix, Nibbles currentPath) {
        if (node == null) {
            return null;
        }

        List<byte[]> childrenMerkleValues = node.getChildrenMerkleValues();
        int startIndex = prefix.size() > currentPath.size() ? prefix.get(currentPath.size()).asInt() : 0;

        for (int i = startIndex; i < childrenMerkleValues.size(); i++) {
            byte[] childMerkleValue = childrenMerkleValues.get(i);
            if (childMerkleValue == null) continue; // Skip empty slots.

            // Fetch the child node based on its merkle value.
            TrieNodeData childNode = getTrieNodeFromMerkleValue(childMerkleValue);

            Nibbles nextPath = currentPath.add(Nibble.fromInt(i)).addAll(childNode.getPartialKey());

            if (nextPath.compareTo(prefix) >= 0) {
                return new StorageNode(nextPath, new NodeData(childNode.getValue(), childMerkleValue));
            }

            StorageNode result = searchForNextBranch(childNode, prefix, nextPath);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Retrieves all keys in the trie that start with a given prefix.
     *
     * @param merkleRoot The root of the trie from which to start searching.
     * @param prefix     The prefix for which matching keys are to be retrieved.
     * @return A list of byte arrays representing the keys that match the given prefix.
     */
    public List<Nibbles> getKeysWithPrefix(byte[] merkleRoot, Nibbles prefix) {
        TrieNodeData rootNode = getTrieNodeFromMerkleValue(merkleRoot);
        if (rootNode == null) {
            return new ArrayList<>();
        }

        List<Nibbles> matchingKeys = new ArrayList<>();
        collectKeysWithPrefix(rootNode, Nibbles.EMPTY, prefix, matchingKeys);
        return matchingKeys;
    }

    private void collectKeysWithPrefix(TrieNodeData node, Nibbles currentPath, Nibbles prefix, List<Nibbles> keys) {
        Nibbles fullPath = currentPath.addAll(node.getPartialKey());

        if (node.getValue() != null && fullPath.startsWith(prefix)) {
            keys.add(fullPath);
        }

        for (byte[] childMerkleValue : node.getChildrenMerkleValues()) {
            if (childMerkleValue == null) continue;
            TrieNodeData childNode = getTrieNodeFromMerkleValue(childMerkleValue);
            if (childNode != null) {
                collectKeysWithPrefix(childNode, fullPath, prefix, keys);
            }
        }
    }

    /**
     * Retrieves keys starting with a given prefix, supporting pagination through a starting key and limit.
     *
     * @param blockStateRoot The state root of the block to search within.
     * @param prefix         The prefix to match against keys in the trie.
     * @param startKey       The key from which to start returning results.
     * @param limit          The maximum number of keys to return.
     * @return A list of byte arrays representing the keys that match the given prefix, starting from the startKey.
     */
    public List<Nibbles> getKeysWithPrefixPaged(Hash256 blockStateRoot, Nibbles prefix, Nibbles startKey, int limit) {
        TrieNodeData rootNode = getTrieNodeFromMerkleValue(blockStateRoot.getBytes());
        if (rootNode == null) {
            return new ArrayList<>();
        }

        List<Nibbles> matchingKeys = new ArrayList<>();
        collectKeysWithPrefix(rootNode, Nibbles.EMPTY, prefix, startKey, limit, matchingKeys, new boolean[]{false});
        return matchingKeys;
    }

    private void collectKeysWithPrefix(TrieNodeData node, Nibbles currentPath, Nibbles prefix, Nibbles startKey,
                                       int limit,
                                       List<Nibbles> keys, boolean[] startKeyFound) {
        if (keys.size() >= limit) return;

        Nibbles fullPath = currentPath.addAll(node.getPartialKey());
        if (node.getValue() != null && fullPath.startsWith(prefix) &&
            startKey == null || startKeyFound[0] || Objects.equals(startKey, fullPath)) {
            if (!startKeyFound[0]) {
                startKeyFound[0] = true;
            } else {
                keys.add(fullPath); // Add key if it matches the prefix and is after the startKey
            }
        }

        // Traverse children
        for (byte[] childMerkleValue : node.getChildrenMerkleValues()) {
            if (childMerkleValue == null) continue;
            TrieNodeData childNode = getTrieNodeFromMerkleValue(childMerkleValue);
            if (childNode != null) {
                collectKeysWithPrefix(childNode, fullPath, prefix, startKey, limit, keys, startKeyFound);
            }
        }
    }

    /**
     * Loads children nodes of a given parent key and Merkle value.
     *
     * @param parentKey         The nibbles representing the parent key.
     * @param parentMerkleValue The Merkle value of the parent node.
     * @return A list of 16 {@link StorageNode}s representing the children of the specified parent node.
     * If no child at position, then the entry is null.
     */
    public List<StorageNode> loadChildren(Nibbles parentKey, byte[] parentMerkleValue) {
        List<byte[]> childrenMerkleValues = Optional.ofNullable(parentMerkleValue)
            .map(this::getTrieNodeFromMerkleValue)
            .map(TrieNodeData::getChildrenMerkleValues)
            .orElseGet(Collections::emptyList);

        List<StorageNode> childrenNodes = new ArrayList<>(Collections.nCopies(childrenMerkleValues.size(), null));
        for (int i = 0; i < childrenMerkleValues.size(); i++) {
            byte[] childMerkleValue = childrenMerkleValues.get(i);
            TrieNodeData childNode = childMerkleValue == null ? null : getTrieNodeFromMerkleValue(childMerkleValue);
            if (childNode != null) {
                Nibbles childKey = parentKey.add(Nibble.fromInt(i)).addAll(childNode.getPartialKey());
                childrenNodes.set(i, new StorageNode(childKey, new NodeData(childNode.getValue(), childMerkleValue)));
            }
        }
        return childrenNodes;
    }

    /**
     * Saves the trie structure to storage.
     * <p>
     * Serializes the given trie into a format suitable for storage and persists it using
     * the specified state version for compatibility.
     *
     * @param trie The trie to serialize and save.
     */
    public void insertTrieStorage(TrieStructure<NodeData> trie) {
        List<InsertTrieNode> dbSerializedTrieNodes = InsertTrieBuilder.build(trie);
        saveTrieNodes(dbSerializedTrieNodes);
    }

    /**
     * Saves only specified nodes from the trie structure to storage.
     *
     * @param trie  The trie to serialize and save.
     * @param nodes Only the nodes specified in the list will be saved.
     */
    public void updateTrieStorage(TrieStructure<NodeData> trie, List<TrieNodeIndex> nodes) {
        List<InsertTrieNode> dbSerializedTrieNodes = InsertTrieBuilder.build(trie, nodes);
        saveTrieNodes(dbSerializedTrieNodes);
    }

    public void updateTrieStorage(Map<Nibbles, PendingInsertUpdate> updates) {
        List<InsertTrieNode> dbSerializedTrieNodes = InsertTrieBuilder.build(updates);
        insertTrieNodeStorageBatch(dbSerializedTrieNodes);
    }

    /**
     * Inserts trie nodes into the key-value repository.
     *
     * @param insertTrieNodes The list of trie nodes to be inserted.
     */
    private void saveTrieNodes(final List<InsertTrieNode> insertTrieNodes) {
        var a = insertTrieNodes.stream().count();
        var counter = 0;
        try {
            for (InsertTrieNode trieNode : insertTrieNodes) {
                counter ++;
                insertTrieNodeStorage(trieNode);
            }
        } catch (Exception e) {
            System.out.println("All: " + a + ", completed: " + counter);
            System.out.println("Failed to insert trie structure to db storage");
        }
    }

    /**
     * Inserts trie node storage data into the key-value repository.
     * <p>
     * The storage data is represented by a {@link TrieNodeData} object, which is serialized and saved to the repository.
     *
     * @param trieNode The trie node whose storage data is to be inserted.
     */
    private void insertTrieNodeStorage(InsertTrieNode trieNode) {
        String key = TRIE_NODE_PREFIX + new String(trieNode.merkleValue());
        db.save(key, toTrieNodeData(trieNode));
    }

    /**
     * Inserts trie nodes storage data into the key-value repository in a batch manner.
     * <p>
     * The storage data is represented by a {@link TrieNodeData} object, which is serialized and saved to the repository.
     *
     * @param insertTrieNodes The trie nodes whose storage data is to be inserted.
     */
    public void insertTrieNodeStorageBatch(List<InsertTrieNode> insertTrieNodes) {
        Map<String, Object> nodesMap = new HashMap<>();

        for (InsertTrieNode trieNode : insertTrieNodes) {
            String key = TRIE_NODE_PREFIX + new String(trieNode.merkleValue());
            nodesMap.put(key, toTrieNodeData(trieNode));
        }

        db.saveBatch(nodesMap);
    }

    private static TrieNodeData toTrieNodeData(InsertTrieNode insertTrieNode) {
        List<byte[]> childrenMerkleValues = insertTrieNode.childrenMerkleValues();

        return new TrieNodeData(
            insertTrieNode.isBranch(),
            insertTrieNode.partialKeyNibbles(),
            childrenMerkleValues,
            insertTrieNode.isReferenceValue() ? null : insertTrieNode.storageValue(),
            insertTrieNode.isReferenceValue() ? insertTrieNode.storageValue() : null,
            (byte) insertTrieNode.stateVersion());
    }

    /**
     * Loads the trie structure from the database starting from a given root.
     *
     * @param trieRoot The Merkle root of the trie to load.
     * @return The reconstructed trie structure, or null if the root is not found.
     */
    public TrieStructure<NodeData> loadTrieStructure(byte[] trieRoot) {
        if (trieRoot == null || trieRoot.length == 0) {
            log.warning("Invalid or empty trie root provided.");
            return null;
        }

        TrieNodeData rootNode = getTrieNodeFromMerkleValue(trieRoot);
        if (rootNode == null) {
            log.warning("No trie node found for the given root.");
            return null;
        }

        TrieStructure<NodeData> trie = new TrieStructure<>();
        loadSubTrie(trie, rootNode, trieRoot, Nibbles.EMPTY);
        return trie;
    }

    /**
     * Recursively loads the sub-trie from the database and adds it to the given trie structure.
     *
     * @param trie            The trie structure being constructed.
     * @param currentNodeData The current node data being processed.
     * @param merkleValue     The Merkle value of the current node.
     * @param currentPath     The nibble path to the current node.
     */
    private void loadSubTrie(TrieStructure<NodeData> trie, TrieNodeData currentNodeData, byte[] merkleValue,
                             Nibbles currentPath) {
        // TODO Known issue:
        // We're inserting all nodes from the DB as key-value-pairs, thus marking a lot of
        // structural branch nodes (i.e. branch nodes with no storage value) as actually having a storage value.
        // Miraculously though, we're still able to calculate proper state root hashes since we're not actually
        // utilising our knowledge of which node has a storage value anywhere in our algorithm, which is a grand
        // coincidence.

        StateVersion stateVersion = StateVersion.fromInt(currentNodeData.getEntriesVersion());
        if (currentNodeData.isBranchNode()) {
            trie.insertBranch(currentPath, new NodeData(null, merkleValue), stateVersion);
        } else {
            byte[] value =
                currentNodeData.getValue() == null
                    ? currentNodeData.getTrieRootRef()
                    : currentNodeData.getValue();
            trie.insertNode(currentPath, new NodeData(value, merkleValue), stateVersion);
        }

        // Recursively load children and construct the trie
        List<byte[]> childrenMerkleValues = currentNodeData.getChildrenMerkleValues();
        for (int i = 0; i < childrenMerkleValues.size(); i++) {
            byte[] childMerkleValue = childrenMerkleValues.get(i);
            if (childMerkleValue != null) {
                TrieNodeData childNodeData = getTrieNodeFromMerkleValue(childMerkleValue);
                if (childNodeData != null) {
                    Nibbles childPath = currentPath.add(Nibble.fromInt(i)).addAll(childNodeData.getPartialKey());
                    loadSubTrie(trie, childNodeData, childMerkleValue, childPath);
                }
            }
        }
    }
}
