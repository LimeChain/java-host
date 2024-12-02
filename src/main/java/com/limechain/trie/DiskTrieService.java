package com.limechain.trie;

import com.google.common.primitives.Bytes;
import com.limechain.exception.trie.TrieTransactionException;
import com.limechain.runtime.version.StateVersion;
import com.limechain.storage.DeleteByPrefixResult;
import com.limechain.storage.trie.TrieStorage;
import com.limechain.trie.cache.TrieChanges;
import com.limechain.trie.cache.node.PendingInsertUpdate;
import com.limechain.trie.cache.node.PendingRemove;
import com.limechain.trie.cache.node.PendingTrieNodeChange;
import com.limechain.trie.dto.node.DecodedNode;
import com.limechain.trie.dto.node.NodeInsertionData;
import com.limechain.trie.dto.node.StorageValue;
import com.limechain.trie.dto.node.TraversedNode;
import com.limechain.trie.structure.nibble.Nibble;
import com.limechain.trie.structure.nibble.Nibbles;
import com.limechain.trie.structure.node.TrieNodeData;
import com.limechain.utils.HashUtils;
import lombok.Getter;
import lombok.extern.java.Log;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This is the main class for the on disk patricia merkle trie. It provides implementation for all the operations that
 * are mostly used by the storage and child storage host functions.
 */
@Log
public class DiskTrieService {

    public static final String UNFINISHED_TRAVERSAL_ERROR =
            "Traversal result cannot be unfinished at this point in the logic";
    private final TrieStorage trieStorage;
    private final List<TrieChanges> transactions = new ArrayList<>();

    private byte[] trieMerkleRoot;

    DiskTrieService(TrieStorage trieStorage, byte[] trieMerkleRoot) {
        this.trieStorage = trieStorage;
        this.trieMerkleRoot = trieMerkleRoot;

        transactions.add(TrieChanges.empty());
    }

    /**
     * A constructor used to create a deep copy of a {@link DiskTrieService}.
     *
     * @param original the instance to copy
     */
    DiskTrieService(DiskTrieService original) {
        trieStorage = original.trieStorage;
        trieMerkleRoot = original.trieMerkleRoot.clone();
        transactions.addAll(original.transactions.stream()
                .map(TrieChanges::copy)
                .toList());
    }

    private TrieChanges getCurrentTrieChanges() {
        if (transactions.isEmpty()) {
            throw new IllegalStateException("The should be at least one trie state loaded in memory.");
        }

        return transactions.getLast();
    }

    public void startTransaction() {
        transactions.add(TrieChanges.copy(getCurrentTrieChanges()));
    }

    public void rollbackTransaction() {
        if (transactions.size() < 2) {
            throw new TrieTransactionException("No active transaction to rollback.");
        }

        transactions.removeLast();
    }

    public void commitTransaction() {
        if (transactions.size() < 2) {
            throw new TrieTransactionException("No active transaction to commit.");
        }

        TrieChanges transaction = transactions.removeLast();
        transactions.set(transactions.size() - 1, transaction);
    }

    /**
     * This method checks the cache for an existing {@link PendingTrieNodeChange}. If a {@link PendingInsertUpdate} is
     * found returns its storage value, otherwise a {@link PendingRemove} means that node at provided key has been
     * deleted in the current block.<br>
     * If no entry is found in cache traverses the disk in search of specified key.
     *
     * @param key the key path for the sought storage value.
     * @return An {@link Optional} with the found storage value or an empty optional otherwise.
     */
    public Optional<byte[]> findStorageValue(Nibbles key) {
        Optional<PendingTrieNodeChange> change = getCurrentTrieChanges().getFromCache(key);
        return change.<Optional<byte[]>>map(pendingTrieNodeChange ->
                        pendingTrieNodeChange instanceof PendingInsertUpdate update
                                ? Optional.ofNullable(update.value())
                                : Optional.empty())
                .orElseGet(() -> traverseTrie(trieMerkleRoot, key) instanceof TraversalResult.Found found
                        ? Optional.ofNullable(found.getFoundNode().getValue())
                        : Optional.empty());
    }

    /**
     * This method traverses the trie from the root (if present in cache starts from there, otherwise from disk) and
     * aims to find the closest following key in a lexicographic manner.
     *
     * @param key the key path whose closest lexicographic successor is needed.
     * @return An {@link Optional} with the found key or and empty one if not found.
     */
    public Optional<Nibbles> getNextKey(Nibbles key) {
        TraversalResult traversalResult = traverseTrie(trieMerkleRoot, key);
        if (traversalResult.getTraversedNodes().isEmpty()) {
            return Optional.empty();
        }

        Optional<Nibbles> result = Optional.empty();

        for (TraversedNode traversedNode : traversalResult.getTraversedNodes().reversed()) {
            TrieNodeData node = new TrieNodeData(traversedNode.getValue() != null,
                    traversedNode.getPartialKey(),
                    traversedNode.getChildrenMerkleValues(),
                    traversedNode.getValue(),
                    null,
                    (byte) traversedNode.getStateVersion().asInt());

            result = findNextKey(key, traversedNode.getFullKey(), node);
            if (result.isPresent()) {
                break;
            }
        }

        return result;
    }

    private Optional<Nibbles> findNextKey(Nibbles prefix, Nibbles currentKey, TrieNodeData node) {
        // A key that is equal or greater and lower in the lexicographical order should be skipped.
        if (currentKey.size() >= prefix.size() && currentKey.compareTo(prefix) < 0) {
            return Optional.empty();
        }
        // If it's a storage node and its key is lexicographically greater than prefix it's a hit.
        if (node.getValue() != null && currentKey.compareTo(prefix) > 0) {
            return Optional.of(currentKey);
        }

        // Skip all lexicographically lower child nibbles to improve performance.
        Nibble startingNibble = currentKey.compareTo(prefix) >= 0
                ? Nibble.ZERO
                : prefix.get(currentKey.size());
        for (Nibble nibble : Nibbles.ALL.drop(startingNibble.asInt())) {
            byte[] childMerkle = node.getChildrenMerkleValues().get(nibble.asInt());
            if (childMerkle == null) {
                continue;
            }

            TrieNodeData cachedChild = getCachedChildAtIndex(currentKey, nibble);
            TrieNodeData childNode = cachedChild != null
                    ? cachedChild
                    : trieStorage.getTrieNodeFromMerkleValue(childMerkle);

            Nibbles nextPath = currentKey.add(nibble).addAll(childNode.getPartialKey());
            Optional<Nibbles> result = findNextKey(prefix, nextPath, childNode);
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * This method either inserts a new node into the trie or updates and existing storage value. The operation that
     * is executed depends on the {@link TraversalResult} at the start of the method. A {@link TraversalResult.Found}
     * results in an update, whereas a {@link TraversalResult.NotFound} executes an insert.
     *
     * @param key          the key path that we insert/update.
     * @param storageValue the new storage value that is to be inserted/updated.
     * @param stateVersion the new state version of the node.
     */
    public void upsertNode(Nibbles key, byte[] storageValue, StateVersion stateVersion) {
        TreeMap<Nibbles, PendingTrieNodeChange> executionUpdates = new TreeMap<>();

        TraversalResult traversalResult = traverseTrie(trieMerkleRoot, key);
        switch (traversalResult) {
            case TraversalResult.NotFound notFound -> executionUpdates.putAll(
                    executeInsert(
                            trieMerkleRoot,
                            new NodeInsertionData(key,
                                    storageValue, stateVersion, notFound.getClosestAncestor().orElse(null))));
            case TraversalResult.Found found -> {
                TraversedNode foundNode = found.getFoundNode();

                assert key.equals(foundNode.getFullKey())
                        : "Found node in traversal result should have the same key path as the sought key";

                executionUpdates.put(foundNode.getFullKey(), toPendingInsertUpdate(
                        storageValue,
                        foundNode.getPartialKey(),
                        foundNode.getStateVersion(),
                        foundNode.getChildrenMerkleValues(),
                        foundNode.getParent() == null
                ));
            }
            case TraversalResult.Unfinished ignored -> throw new IllegalStateException(UNFINISHED_TRAVERSAL_ERROR);
        }

        Map.Entry<Nibbles, PendingTrieNodeChange> closestSuccessor = executionUpdates.firstEntry();
        executionUpdates.putAll(toPendingInsertUpdates(traversalResult.traversedNodes,
                closestSuccessor.getKey(),
                closestSuccessor.getValue()));

        getCurrentTrieChanges().updateCache(executionUpdates);
    }

    /**
     * This method analyzes the existing trie and creates the node that is to be inserted into the cache later down the
     * line. It also deals with creating new branch nodes if needed, updating children partials keys.
     *
     * @param merkleRoot    the merkle value of the trie root.
     * @param insertionData object that holds all data needed for the insertion of the new node.
     * @return A {@link TreeMap} of the newly created node and its updated parent/children if any.
     */
    private TreeMap<Nibbles, PendingInsertUpdate> executeInsert(byte[] merkleRoot, NodeInsertionData insertionData) {
        boolean isRoot = getCurrentTrieChanges().getRoot().isEmpty() &&
                trieStorage.getTrieNodeFromMerkleValue(merkleRoot) == null;

        // If trie is empty insert the newly created node as root.
        if (isRoot) {
            TreeMap<Nibbles, PendingInsertUpdate> result = new TreeMap<>();
            List<byte[]> newNodeChildren = new ArrayList<>(Collections.nCopies(16, null));

            result.put(insertionData.getKey(), toPendingInsertUpdate(
                    insertionData.getNewNodeValue(),
                    insertionData.getKey(),
                    insertionData.getNewNodeStateVersion(),
                    newNodeChildren,
                    true));

            return result;
        }

        int ancestorKeySize = 0;
        byte[] existingNodeMerkle;
        TrieNodeData existingCachedNode = null;

        if (insertionData.getAncestor() == null) {
            // If no ancestor was found and trie is not empty, the root becomes the successor node.
            existingNodeMerkle = merkleRoot;
        } else {
            // If ancestor was found we check the new node's child index in its ancestor.
            ancestorKeySize = insertionData.getAncestor().getFullKey().size();
            Nibble newNodeChildIndex = insertionData.getKey().get(ancestorKeySize);
            byte[] merkleAtNewNodeIndex = insertionData.getAncestor().getChildrenMerkleValues()
                    .get(newNodeChildIndex.asInt());

            if (merkleAtNewNodeIndex == null) {
                // There is an empty slot in `futureParentNode` for our new node.
                //
                //
                //           `future_parent`
                //                 +-+
                //             +-> +-+  <---------+
                //             |    ^ ^------+    |
                //             |    +--+     |    |
                //            +-+      |     |    |
                //   New node +-+     +-+   +-+  +-+  0 or more existing children
                //                    +-+   +-+  +-+
                //
                //

                TreeMap<Nibbles, PendingInsertUpdate> result = new TreeMap<>();
                List<byte[]> newNodeChildren = new ArrayList<>(Collections.nCopies(16, null));

                result.put(insertionData.getKey(), toPendingInsertUpdate(
                        insertionData.getNewNodeValue(),
                        insertionData.getKey().drop(ancestorKeySize + 1),
                        insertionData.getNewNodeStateVersion(),
                        newNodeChildren,
                        false));

                return result;
            } else {
                // If child index in ancestor is not empty it means there is a node sharing the new key path.
                existingNodeMerkle = merkleAtNewNodeIndex;
                // We check the cache for the said node.
                existingCachedNode = getCachedChildAtIndex(insertionData.getAncestor().getFullKey(), newNodeChildIndex);
            }
        }

        // If the node that shares the key path is not in cache we check the disk.
        TrieNodeData existingNode = existingCachedNode != null
                ? existingCachedNode
                : trieStorage.getTrieNodeFromMerkleValue(existingNodeMerkle);

        Nibbles existingNodePartialKey = existingNode.getPartialKey();
        Nibbles newNodePartialKey = insertionData.getKey().drop(ancestorKeySize + 1);

        // If `existingNodePartialKey` starts with `newNodePartialKey`, then the new node
        // will be inserted in-between the parent and the existing node.
        if (existingNodePartialKey.startsWith(newNodePartialKey)) {
            return createChildInsert(insertionData, existingNodePartialKey, newNodePartialKey, existingNode);
        }

        return createBranchInsert(insertionData, newNodePartialKey, existingNodePartialKey, ancestorKeySize, existingNode);
    }

    private static TreeMap<Nibbles, PendingInsertUpdate> createChildInsert(NodeInsertionData insertionData,
                                                                           Nibbles existingNodePartialKey,
                                                                           Nibbles newNodePartialKey,
                                                                           TrieNodeData existingNode) {
        // The new node is to be inserted in-between `futureParent` and
        // `existingNodeIndex`.
        //
        // If `futureParent` is not null:
        //
        //
        //                         +-+
        //        `futureParent`   +-+ <---------+
        //                          ^            |
        //                          |            +
        //                         +-+         (0 or more existing children)
        //               New node  +-+
        //                          ^
        //                          |
        //                         +-+
        //    `existingNodeIndex`  +-+
        //                          ^
        //                          |
        //                          +
        //                    (0 or more existing children)
        //
        //
        //
        // If `futureParent` is null:
        //
        //
        //            New node    +-+
        //    (becomes the root)  +-+
        //                         ^
        //                         |
        //   `existingNodeIndex`  +-+
        //     (current root)     +-+
        //                         ^
        //                         |
        //                         +
        //                   (0 or more existing children)
        //
        TreeMap<Nibbles, PendingInsertUpdate> result = new TreeMap<>();
        List<byte[]> newNodeChildren = new ArrayList<>(Collections.nCopies(16, null));

        Nibbles existingNodeFullKey = insertionData.getKey().addAll(
                existingNodePartialKey.drop(newNodePartialKey.size()));

        PendingInsertUpdate existing = result.computeIfAbsent(existingNodeFullKey,
                (k) -> toPendingInsertUpdate(
                        existingNode.getValue(),
                        existingNodePartialKey.drop(newNodePartialKey.size() + 1),
                        StateVersion.fromInt(existingNode.getEntriesVersion()),
                        existingNode.getChildrenMerkleValues(),
                        false
                ));

        Nibble existingNodeIndexInNewNode = existingNodePartialKey.get(newNodePartialKey.size());
        newNodeChildren.set(existingNodeIndexInNewNode.asInt(), existing.newMerkleValue());
        result.put(insertionData.getKey(), toPendingInsertUpdate(
                insertionData.getNewNodeValue(),
                newNodePartialKey,
                insertionData.getNewNodeStateVersion(),
                newNodeChildren,
                insertionData.getAncestor() == null
        ));

        return result;
    }

    private static TreeMap<Nibbles, PendingInsertUpdate> createBranchInsert(NodeInsertionData insertionData,
                                                                            Nibbles newNodePartialKey,
                                                                            Nibbles existingNodePartialKey,
                                                                            int ancestorKeySize,
                                                                            TrieNodeData existingNode) {
        // If we reach here, we know that we will need to create a new branch node in addition to
        // the new storage node.
        //
        // If `futureParent` is not null:
        //
        //
        //                  `futureParent`
        //
        //                        +-+
        //                        +-+ <--------+  (0 or more existing children)
        //                         ^
        //                         |
        //       New branch node  +-+
        //                        +-+ <-------+
        //                         ^          |
        //                         |          |
        //                        +-+        +-+
        //   `existingNodeIndex`  +-+        +-+  New storage node
        //                         ^
        //                         |
        //
        //                 (0 or more existing children)
        //
        //
        //
        // If `futureParent` is null:
        //
        //
        //     New branch node    +-+
        //     (becomes root)     +-+ <-------+
        //                         ^          |
        //                         |          |
        //   `existingNodeIndex`  +-+        +-+
        //     (current root)     +-+        +-+  New storage node
        //                         ^
        //                         |
        //
        //                 (0 or more existing children)
        //
        //
        int branchPartialKeyLen = (int) IntStream
                .range(0, Math.min(newNodePartialKey.size(), existingNodePartialKey.size()))
                .takeWhile(i -> newNodePartialKey.get(i).equals(existingNodePartialKey.get(i)))
                .count();

        TreeMap<Nibbles, PendingInsertUpdate> result = new TreeMap<>();

        Nibbles existingNodeFullKey = insertionData.getKey().take(ancestorKeySize + 1)
                .addAll(existingNodePartialKey);

        PendingInsertUpdate existingStorage = result.computeIfAbsent(existingNodeFullKey,
                (k) -> toPendingInsertUpdate(
                        existingNode.getValue(),
                        existingNodePartialKey.drop(branchPartialKeyLen + 1),
                        StateVersion.fromInt(existingNode.getEntriesVersion()),
                        existingNode.getChildrenMerkleValues(),
                        false
                ));

        PendingInsertUpdate newStorage = result.computeIfAbsent(insertionData.getKey(),
                (k) -> toPendingInsertUpdate(
                        insertionData.getNewNodeValue(),
                        newNodePartialKey.drop(branchPartialKeyLen + 1),
                        insertionData.getNewNodeStateVersion(),
                        new ArrayList<>(Collections.nCopies(16, null)),
                        false
                ));

        List<byte[]> newBranchChildren = new ArrayList<>(Collections.nCopies(16, null));
        Nibble existingNodeIndexInBranchNode = existingNodePartialKey.get(branchPartialKeyLen);
        newBranchChildren.set(existingNodeIndexInBranchNode.asInt(), existingStorage.newMerkleValue());
        Nibble newNodeIndexInBranchNode = newNodePartialKey.get(branchPartialKeyLen);
        newBranchChildren.set(newNodeIndexInBranchNode.asInt(), newStorage.newMerkleValue());

        Nibbles branchNodeFullKey = insertionData.getKey().take(ancestorKeySize + 1 + branchPartialKeyLen);

        result.put(branchNodeFullKey, toPendingInsertUpdate(
                null,
                newNodePartialKey.take(branchPartialKeyLen),
                insertionData.getNewNodeStateVersion(),
                newBranchChildren,
                insertionData.getAncestor() == null
        ));

        return result;
    }

    /**
     * This method checks if a child entry exists in the cache.
     *
     * @param parentFullKey key path of the parent node.
     * @param childIndex    index of the child in the parent.
     * @return A {@link TrieNodeData} representation of the cached child if found, null otherwise.
     */
    @Nullable
    private TrieNodeData getCachedChildAtIndex(Nibbles parentFullKey, Nibble childIndex) {
        Optional<PendingInsertUpdate> cached = getCurrentTrieChanges().getChildByIndex(parentFullKey, childIndex);

        if (cached.isPresent()) {
            PendingInsertUpdate update = cached.get();
            return new TrieNodeData(
                    update.value() == null,
                    update.partialKey(),
                    new ArrayList<>(update.childrenMerkleValues()),
                    update.value(),
                    null,
                    (byte) update.stateVersion().asInt()
            );
        }

        return null;
    }

    /**
     * This method deletes the node at the specified key path if found.
     *
     * @param key the key path that we want to delete.
     */
    public void deleteStorageNode(Nibbles key) {
        TraversalResult traversalResult = traverseTrie(trieMerkleRoot, key);

        switch (traversalResult) {
            case TraversalResult.Found found -> {
                TreeMap<Nibbles, PendingTrieNodeChange> executionUpdates = executeDeletion(found.getFoundNode());
                getCurrentTrieChanges().updateCache(mergeDeletionUpdatesWithTraversed(
                        executionUpdates, traversalResult.traversedNodes));
            }
            case TraversalResult.NotFound ignored -> log.fine("DELETE: Node not found at key " + key);
            case TraversalResult.Unfinished ignored -> throw new IllegalStateException(UNFINISHED_TRAVERSAL_ERROR);
        }
    }

    /**
     * This method deleted nodes recursively starting from nodes with the biggest depth, which match the prefix.
     *
     * @param prefix the key path prefix that nodes should match if they are to be deleted.
     * @param limit  the maximum number of nodes to delete before returning a result.
     * @return A {@link DeleteByPrefixResult} which shows the number of deleted nodes and if all matching nodes are
     * removed.
     */
    public DeleteByPrefixResult deleteMultipleNodesByPrefix(Nibbles prefix, Long limit) {
        TraversalResult traversalResult = traverseTrie(trieMerkleRoot, prefix);

        switch (traversalResult) {
            case TraversalResult.Found found -> {
                AtomicInteger deleted = new AtomicInteger(0);

                TraversedNode node = found.getFoundNode();

                Nibbles.ALL.forEach(n -> {
                    byte[] childMerkle = node.getChildrenMerkleValues().get(n.asInt());
                    // If there is a child at given nibble we continue with recursive deletion.
                    if (childMerkle != null) {
                        Optional<PendingTrieNodeChange> recursionResult = executeRecursiveDeletion(
                                node.getFullKey(), n, childMerkle, limit, deleted);
                        // If recursion returns a non-empty optional there have been changes during execution.
                        recursionResult.ifPresent(r -> {
                            node.getChildrenMerkleValues().set(n.asInt(),
                                    r instanceof PendingInsertUpdate c
                                            // If last change is an update set the new child merkle.
                                            ? c.newMerkleValue()
                                            // If it's a deletion set to null.
                                            : null);
                        });
                    }
                });

                if (limit != null && deleted.get() >= limit) {
                    return new DeleteByPrefixResult(deleted.get(), false);
                }

                if (node.getValue() != null) {
                    deleted.incrementAndGet();
                }

                TreeMap<Nibbles, PendingTrieNodeChange> executionUpdates = executeDeletion(node);
                getCurrentTrieChanges().updateCache(mergeDeletionUpdatesWithTraversed(
                        executionUpdates, traversalResult.traversedNodes));

                return new DeleteByPrefixResult(deleted.get(), true);
            }
            case TraversalResult.NotFound ignored -> {
                log.fine("DELETE: Node not found at key " + prefix);
                return new DeleteByPrefixResult(0, true);
            }
            case TraversalResult.Unfinished ignored -> throw new IllegalStateException(UNFINISHED_TRAVERSAL_ERROR);
        }
    }

    private Optional<PendingTrieNodeChange> executeRecursiveDeletion(Nibbles parentFullKey,
                                                                     Nibble indexInParent,
                                                                     byte[] childMerkle,
                                                                     Long limit,
                                                                     AtomicInteger deleted) {
        if (limit != null && deleted.get() >= limit) {
            return Optional.empty();
        }

        TrieNodeData cachedChild = getCachedChildAtIndex(parentFullKey, indexInParent);
        TrieNodeData childNode = cachedChild != null
                ? cachedChild
                : trieStorage.getTrieNodeFromMerkleValue(childMerkle);
        Nibbles foundNodeFullKey = parentFullKey.add(indexInParent).addAll(childNode.getPartialKey());
        List<byte[]> foundNodeChildrenCopy = new ArrayList<>(childNode.getChildrenMerkleValues());

        Optional<PendingTrieNodeChange> recursionResult;
        for (Nibble nibble : Nibbles.ALL) {
            byte[] childMerkleInner = foundNodeChildrenCopy.get(nibble.asInt());
            // If there is a child at given nibble we continue with recursive deletion.
            if (childMerkleInner != null) {
                recursionResult = executeRecursiveDeletion(foundNodeFullKey, nibble, childMerkleInner, limit, deleted);
                // If recursion returns a non-empty optional there have been changes during execution.
                recursionResult.ifPresent(r -> {
                    foundNodeChildrenCopy.set(nibble.asInt(),
                            r instanceof PendingInsertUpdate c
                                    // If last change is an update set the new child merkle.
                                    ? c.newMerkleValue()
                                    // If it's a deletion set to null.
                                    : null);
                });
            }
        }

        if (limit == null || deleted.get() < limit) {
            PendingTrieNodeChange remove = new PendingRemove();
            getCurrentTrieChanges().updateCache(new TreeMap<>(Map.of(foundNodeFullKey, remove)));
            if (childNode.getValue() != null) {
                deleted.incrementAndGet();
            }
            return Optional.of(remove);
        } else {
            PendingTrieNodeChange update = toPendingInsertUpdate(
                    childNode.getValue(),
                    childNode.getPartialKey(),
                    StateVersion.fromInt(childNode.getEntriesVersion()),
                    foundNodeChildrenCopy,
                    false
            );
            getCurrentTrieChanges().updateCache(new TreeMap<>(Map.of(foundNodeFullKey, update)));
            return Optional.of(update);
        }
    }

    private TreeMap<Nibbles, PendingTrieNodeChange> executeDeletion(TraversedNode found) {
        TreeMap<Nibbles, PendingTrieNodeChange> result = new TreeMap<>();

        TraversedNode parent = found.getParent();

        if (parent == null) {
            result.put(found.getFullKey(), new PendingRemove());
            return result;
        }

        long foundChildrenCount = found.getChildrenMerkleValues().stream()
                .filter(Objects::nonNull)
                .count();

        if (foundChildrenCount == 0) {
            result.putAll(executeDeletionNoChildren(found, parent));
        } else if (foundChildrenCount == 1) {
            // If the node has one child we need to merge them and point the parent to the newly merged node.
            // Before deletion:
            //                      Parent
            //                        +-+
            //                        +-+
            //                         ^
            //                         |
            //     Node to be deleted +-+
            //                        +-+
            //                         ^
            //                         |
            //                        +-+
            //                  Child +-+
            //
            // After deletion:
            //                      Parent
            //                        +-+
            //                        +-+
            //                         ^
            //                         |
            //     Node to be deleted +-+
            //     merged with child  +-+
            //
            Map.Entry<Nibbles, PendingInsertUpdate> mergedUpdate = mergeParentIntoChild(found);
            assert mergedUpdate != null : "Merge result should not be null";
            result.put(mergedUpdate.getKey(), mergedUpdate.getValue());
            result.put(found.getFullKey(), new PendingRemove());
        } else {
            result.put(found.getFullKey(), toPendingInsertUpdate(null,
                    found.getPartialKey(),
                    found.getStateVersion(),
                    found.getChildrenMerkleValues(),
                    false));
        }

        return result;
    }

    private TreeMap<Nibbles, PendingTrieNodeChange> executeDeletionNoChildren(TraversedNode found,
                                                                              TraversedNode parent) {
        TreeMap<Nibbles, PendingTrieNodeChange> result = new TreeMap<>();
        result.put(found.getFullKey(), new PendingRemove());

        long parentChildrenCount = parent.getChildrenMerkleValues().stream()
                .filter(Objects::nonNull)
                .count();

        //A leaf node being single child of its parent is invalid scenario.
        assert !(parentChildrenCount == 1 && parent.getValue() == null) : "Unreachable state.";

        // If the leaf node is one of 2 children of a branch node we need to merge the parent with the other child
        // after the deletion.
        // Before deletion:
        //                      grand parent
        //                        +-+
        //                        +-+
        //                         ^
        //                         |
        //                parent  +-+
        //                        +-+ <-------+
        //                         ^          |
        //                         |          |
        //                        +-+        +-+
        //           Second child +-+        +-+  Node to be deleted
        //
        // After deletion:
        //
        //                      grand parent
        //                        +-+
        //                        +-+
        //                         ^
        //                         |
        //        Parent merged   +-+
        //     with second child  +-+
        //
        if (parentChildrenCount == 2) {
            Nibble foundIndexInParent = found.getFullKey().get(parent.getFullKey().size());
            parent.getChildrenMerkleValues().set(foundIndexInParent.asInt(), null);

            Map.Entry<Nibbles, PendingInsertUpdate> mergedUpdate = mergeParentIntoChild(parent);
            assert mergedUpdate != null : "Merge result should not be null";

            // Add merged parent + child update.
            result.put(mergedUpdate.getKey(), mergedUpdate.getValue());
            // Remove old parent from cache.
            getCurrentTrieChanges().removeFromCache(parent.getFullKey());

            // Point grandparent to newly merged parent + child.
            TraversedNode grandParent = parent.getParent();
            Nibble foundIndexInGrandpa = parent.getFullKey().get(grandParent.getFullKey().size());
            grandParent.getChildrenMerkleValues().set(
                    foundIndexInGrandpa.asInt(), mergedUpdate.getValue().newMerkleValue());
            result.put(grandParent.getFullKey(), toPendingInsertUpdate(
                    grandParent.getValue(),
                    grandParent.getPartialKey(),
                    grandParent.getStateVersion(),
                    grandParent.getChildrenMerkleValues(),
                    grandParent.getParent() == null
            ));
        }

        return result;
    }

    /**
     * This method merges the provided node into its child.
     *
     * @param node the parent node that need be merged with its child. It should have only a single non-null child.
     * @return A map entry of merged nodes full key and its {@link PendingInsertUpdate} representation.
     */
    @Nullable
    private Map.Entry<Nibbles, PendingInsertUpdate> mergeParentIntoChild(TraversedNode node) {
        for (int i = 0; i < node.getChildrenMerkleValues().size(); i++) {
            byte[] childMerkle = node.getChildrenMerkleValues().get(i);
            if (childMerkle != null) {
                Nibble indexInParent = Nibble.fromInt(i);
                TrieNodeData cachedChild = getCachedChildAtIndex(node.getFullKey(), indexInParent);
                TrieNodeData child = cachedChild != null
                        ? cachedChild
                        : trieStorage.getTrieNodeFromMerkleValue(childMerkle);
                Nibbles mergedPartialKey = node.getPartialKey()
                        .add(indexInParent)
                        .addAll(child.getPartialKey());
                Nibbles mergeFullKey = node.getFullKey()
                        .add(indexInParent)
                        .addAll(child.getPartialKey());

                return Map.entry(mergeFullKey, toPendingInsertUpdate(
                        child.getValue(),
                        mergedPartialKey,
                        StateVersion.fromInt(child.getEntriesVersion()),
                        child.getChildrenMerkleValues(),
                        node.getParent() == null
                ));
            }
        }

        return null;
    }

    /**
     * This method persists the changes from the cache layer to the disk. It also clears the cache.
     */
    public void persistChanges() {
        Map<Nibbles, PendingInsertUpdate> updates = getCurrentTrieChanges().getChanges().entrySet().stream()
                .filter(e -> e.getValue() instanceof PendingInsertUpdate)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (PendingInsertUpdate) e.getValue()));

        trieStorage.updateTrieStorage(updates);

        getCurrentTrieChanges().clear();
    }

    /**
     * Returns the current calculated trie root merkle.
     *
     * @return a byte array of the merkle root.
     */
    public byte[] getMerkleRoot() {
        getCurrentTrieChanges().getRoot().ifPresent(r -> trieMerkleRoot = r.newMerkleValue());
        return trieMerkleRoot;
    }

    /**
     * This method traverses the cache and disk layers.
     *
     * @param merkleRoot used as a starting point for the disk traversal when no entries were found in the cache layer.
     * @param key        the sought key path.
     * @return {@link TraversalResult} that represents the result of traversing the cache and disk layers.
     */
    private TraversalResult traverseTrie(byte[] merkleRoot, Nibbles key) {
        TraversalResult cacheTraverseResult = traverseCache(key);

        if (cacheTraverseResult instanceof TraversalResult.Unfinished tr) {
            TraversalResult diskTraverseResult;
            if (cacheTraverseResult.getTraversedNodes().isEmpty()) {
                diskTraverseResult = traverseDiskFromRoot(merkleRoot, key);
            } else {
                TraversedNode ancestor = cacheTraverseResult.getClosestAncestor().orElse(null);
                assert ancestor != null : "Ancestor cannot be bull at this point.";
                diskTraverseResult = traverseDiskFromAncestor(ancestor, key);
            }

            return tr.finishTraversal(diskTraverseResult);
        }

        return cacheTraverseResult;
    }

    /**
     * This method traverses the cache layer.
     *
     * @param key the sought key path.
     * @return {@link TraversalResult} that represents the result of traversing the cache layer.
     */
    private TraversalResult traverseCache(Nibbles key) {
        List<Map.Entry<Nibbles, PendingInsertUpdate>> entriesInKeyPath =
                getCurrentTrieChanges().getEntriesInKeyPath(PendingInsertUpdate.class, key);

        // If cache is empty or no cached entries are in sought key path continue to disk traverse.
        if (getCurrentTrieChanges().isCacheEmpty() || entriesInKeyPath.isEmpty()) {
            return new TraversalResult.Unfinished(new ArrayList<>());
        }

        List<TraversedNode> traversedNodes = toTraversedNodes(entriesInKeyPath);

        // If cache contains exact key match we have 2 options. If it's pending a deletion we return a NotFound result,
        // Found otherwise. No need for disk traversal after this.
        if (getCurrentTrieChanges().isKeyInCache(key)) {
            TraversedNode last = traversedNodes.getLast();
            TraversalResult result;

            if (last.getFullKey().equals(key)) {
                traversedNodes.removeLast();
                result = new TraversalResult.Found(traversedNodes, last);
            } else {
                result = new TraversalResult.NotFound(traversedNodes);
            }

            return result;
        }

        // If we reach here we know that the closest ancestor is in the cache map.
        TraversedNode closestAncestor = traversedNodes.getLast();
        Nibble indexInClosestAncestor = key.get(closestAncestor.getFullKey().size());

        // If the first nibble of the sought key (minus the full key of the closest ancestor) is present in the ancestor
        // this means that there is a following node, so we need to continue traversal in db from that node.
        if (closestAncestor.getChildrenMerkleValues().get(indexInClosestAncestor.asInt()) != null) {
            return new TraversalResult.Unfinished(traversedNodes);
        }

        // Else if no entry is present at the child index no node at the key path exists yet.
        return new TraversalResult.NotFound(traversedNodes);
    }

    /**
     * This method starts a traversal in the disk layer from a provided ancestor. The idea is to skip disk operations
     * to reach the ancestor.
     *
     * @param ancestor node from which to start the traversal in the disk.
     * @param key      the sought key path.
     * @return {@link TraversalResult} that represents the result of traversing the disk layer.
     */
    private TraversalResult traverseDiskFromAncestor(TraversedNode ancestor, Nibbles key) {
        Nibble childIndexInAncestor = key.get(ancestor.getFullKey().size());
        byte[] childMerkle = ancestor.getChildrenMerkleValues().get(childIndexInAncestor.asInt());
        return traverseDisk(childMerkle, key, ancestor.getFullKey().add(childIndexInAncestor));
    }

    /**
     * This method starts a traversal in the disk layer from the root. This method is used when the cache layer found
     * no ancestor.
     *
     * @param merkleRoot the merkle root of the trie.
     * @param key        the sought key path.
     * @return {@link TraversalResult} that represents the result of traversing the disk layer.
     */
    private TraversalResult traverseDiskFromRoot(byte[] merkleRoot, Nibbles key) {
        return traverseDisk(merkleRoot, key, Nibbles.EMPTY);
    }


    private TraversalResult traverseDisk(byte[] merkleRoot,
                                         Nibbles key,
                                         Nibbles ancestorKeyWithChildIndex) {
        TrieNodeData rootNode = trieStorage.getTrieNodeFromMerkleValue(merkleRoot);

        if (rootNode == null) {
            return new TraversalResult.NotFound(new ArrayList<>());
        }

        byte[] currentMerkleValue = merkleRoot;
        List<TraversedNode> traversed = new ArrayList<>();
        Nibbles currentKey = ancestorKeyWithChildIndex;
        Iterator<Nibble> keyIter = key.drop(currentKey.size()).iterator();

        while (true) {
            TrieNodeData currentNode = trieStorage.getTrieNodeFromMerkleValue(currentMerkleValue);

            // First, we must remove `currentNode`'s partial key from `key`, making sure that they
            // match.
            for (Nibble nibble : currentNode.getPartialKey()) {
                if (!keyIter.hasNext() || !keyIter.next().equals(nibble)) {
                    return new TraversalResult.NotFound(traversed);
                }
            }

            currentKey = currentKey.addAll(currentNode.getPartialKey());

            TraversedNode currentTraversed = new TraversedNode(currentKey,
                    currentNode.getPartialKey(),
                    new ArrayList<>(currentNode.getChildrenMerkleValues()),
                    StateVersion.fromInt(currentNode.getEntriesVersion()),
                    currentNode.getValue(),
                    traversed.isEmpty()
                            ? null
                            : traversed.getLast());

            // If no next nibble is present in the key, return successfully...
            if (!keyIter.hasNext()) {
                return new TraversalResult.Found(traversed, currentTraversed);
            }

            traversed.add(currentTraversed);

            // ... otherwise, parse the next nibble as `childIndex`
            Nibble childIndex = keyIter.next();
            byte[] nextMerkleValue = currentTraversed.getChildrenMerkleValues().get(childIndex.asInt());

            // If the `current` trie node doesn't contain a child with that next nibble
            // return `NotFound`...
            if (nextMerkleValue == null) {
                return new TraversalResult.NotFound(traversed);
            }

            // ... otherwise, continue with the traversal
            currentMerkleValue = nextMerkleValue;
            currentKey = currentKey.add(childIndex);
        }
    }

    private static Map<Nibbles, PendingInsertUpdate> toPendingInsertUpdates(List<TraversedNode> toUpdate,
                                                                            Nibbles successorKey,
                                                                            PendingTrieNodeChange successor) {
        Map<Nibbles, PendingInsertUpdate> pendingUpdates = new HashMap<>();

        Nibbles successorFullKey = successorKey.copy();
        byte[] successorMerkleValue = successor instanceof PendingInsertUpdate s
                ? s.newMerkleValue()
                : null;

        for (TraversedNode traversedNode : toUpdate.reversed()) {
            Nibble successorChildIndex = successorFullKey.get(traversedNode.getFullKey().size());
            traversedNode.getChildrenMerkleValues().set(successorChildIndex.asInt(), successorMerkleValue);

            PendingInsertUpdate change = pendingUpdates.compute(traversedNode.getFullKey(), (k, v) ->
                    toPendingInsertUpdate(traversedNode.getValue(),
                            traversedNode.getPartialKey(),
                            traversedNode.getStateVersion(),
                            traversedNode.getChildrenMerkleValues(),
                            traversedNode.getParent() == null
                    ));

            successorFullKey = traversedNode.getFullKey();
            successorMerkleValue = change.newMerkleValue();
        }

        return pendingUpdates;
    }

    private static PendingInsertUpdate toPendingInsertUpdate(@Nullable byte[] value,
                                                             Nibbles partialKey,
                                                             StateVersion stateVersion,
                                                             List<byte[]> childrenMerkleValues,
                                                             boolean isRoot) {
        List<List<Byte>> childrenMerkles = childrenMerkleValues.stream()
                .map(Optional::ofNullable)
                .map(o -> o
                        .map(Bytes::asList)
                        .orElse(null))
                .toList();

        DecodedNode<List<Byte>> decoded = new DecodedNode<>(
                childrenMerkles,
                partialKey,
                constructStorageValue(value, stateVersion));

        byte[] merkleValue = decoded.calculateMerkleValue(HashUtils::hashWithBlake2b, isRoot);

        return new PendingInsertUpdate(merkleValue, childrenMerkleValues, partialKey, stateVersion, value);
    }

    private static List<TraversedNode> toTraversedNodes(List<Map.Entry<Nibbles, PendingInsertUpdate>> entries) {
        if (entries.isEmpty()) {
            return new ArrayList<>();
        }

        List<TraversedNode> result = new ArrayList<>();
        TraversedNode parent = null;

        for (Map.Entry<Nibbles, PendingInsertUpdate> e : entries) {
            PendingInsertUpdate currentPendingChange = e.getValue();

            TraversedNode newTraversalNode = new TraversedNode(e.getKey(),
                    currentPendingChange.partialKey(),
                    new ArrayList<>(currentPendingChange.childrenMerkleValues()),
                    currentPendingChange.stateVersion(),
                    currentPendingChange.value(),
                    parent);

            parent = newTraversalNode;
            result.add(newTraversalNode);
        }

        return result;
    }

    @Nullable
    private static StorageValue constructStorageValue(@Nullable byte[] value, StateVersion stateVersion) {
        if (value == null) {
            return null;
        }

        if (stateVersion == StateVersion.V1 && value.length >= 33) {
            return new StorageValue(HashUtils.hashWithBlake2b(value), true);
        }

        return new StorageValue(value, false);
    }

    /**
     * This method creates the needed {@link  PendingTrieNodeChange} for a deletion operation based on updates from
     * a deletion execution and previously traversed nodes.
     *
     * @param pendingUpdates cache layer changes generated after executing a deletion operation.
     * @param traversedNodes a list of nodes traversed before the deletion operation.
     * @return A {@link TreeMap} of cache updates with newly calculated merkles.
     */
    private static TreeMap<Nibbles, PendingTrieNodeChange> mergeDeletionUpdatesWithTraversed(
            TreeMap<Nibbles, PendingTrieNodeChange> pendingUpdates,
            List<TraversedNode> traversedNodes) {
        TreeMap<Nibbles, PendingTrieNodeChange> result = new TreeMap<>(pendingUpdates);

        // In some deletion edge cases we have pending updates to neighboring nodes as well.
        // Those are the ones we'd like to point to in the traversed nodes.
        Map.Entry<Nibbles, PendingTrieNodeChange> closestSuccessor = result.size() != 1
                ? result.entrySet().stream()
                .filter(e -> e.getValue() instanceof PendingInsertUpdate)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("There should be at least one pending update."))
                : result.firstEntry();

        // In the edge case of merging a branch into it's only left child after deletion we've already calculated
        // the merkle change in the grandparent and removed the parent as it is merged in the child.
        List<TraversedNode> toUpdate = traversedNodes;
        if (result.size() == 3) {
            toUpdate = toUpdate.subList(0, toUpdate.size() - 2);
        }

        result.putAll(toPendingInsertUpdates(toUpdate,
                closestSuccessor.getKey(),
                closestSuccessor.getValue()));

        return result;
    }

    /**
     * Represents the outcome of a trie traversal operation.
     * Because we check our cache layer first, see {@link TrieChanges}{@code .changes},
     * there is a chance that the traversal will result in an {@link Unfinished} result.
     * This means that the traversal has to continue in the database stored nodes.
     */
    @Getter
    private abstract static sealed class TraversalResult {

        /**
         * All the nodes visited during the traversal. Could be empty in case of an empty trie or when the sought key
         * is lexicographically before the current root.
         */
        private final List<TraversedNode> traversedNodes;

        private TraversalResult(List<TraversedNode> traversedNodes) {
            this.traversedNodes = traversedNodes;
        }

        /**
         * In the case of a non-empty traversal list the last entry will always be the closest ancestor of the result.
         *
         * @return The closest ancestor node at this point of the traversal.
         */
        Optional<TraversedNode> getClosestAncestor() {
            if (traversedNodes.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(traversedNodes.getLast());
        }

        /**
         * Traversal is finished and a node at the sought key path was found.
         */
        @Getter
        private static final class Found extends DiskTrieService.TraversalResult {
            private final TraversedNode foundNode;

            private Found(List<TraversedNode> traversedNodes, TraversedNode found) {
                super(traversedNodes);

                this.foundNode = found;
            }
        }

        /**
         * Traversal is finished and no node at the sought key path was found.
         */
        private static final class NotFound extends DiskTrieService.TraversalResult {
            private NotFound(List<TraversedNode> traversedNodes) {
                super(traversedNodes);
            }
        }

        /**
         * Traversal has gone through the cache layer, but has not resulted in a {@link Found}, nor a {@link NotFound}
         * result. A follow-up traversal through the disk needs to be done.
         */
        private static final class Unfinished extends DiskTrieService.TraversalResult {
            private Unfinished(List<TraversedNode> traversedNodes) {
                super(traversedNodes);
            }

            /**
             * Concatenates the results of the unfinished traversal with a finished one.
             *
             * @param finishedTraversal a finished traversal with either a found or a non found result.
             * @return a finished traversal with a concatenated list of nodes from both traversals.
             */
            TraversalResult finishTraversal(TraversalResult finishedTraversal) {
                if (!getTraversedNodes().isEmpty()) {
                    TraversedNode parent = getTraversedNodes().getLast();
                    // The last traversed node in the unfinished result becomes the parent of the first in the finished.
                    switch (finishedTraversal) {
                        case Found found -> {
                            TraversedNode child = found.getTraversedNodes().isEmpty()
                                    ? found.getFoundNode()
                                    : found.getTraversedNodes().getFirst();
                            child.setParent(parent);
                        }
                        case NotFound notFound -> {
                            if (!notFound.getTraversedNodes().isEmpty()) {
                                TraversedNode child = notFound.getTraversedNodes().getFirst();
                                child.setParent(parent);
                            }
                        }
                        case Unfinished ignored ->
                                throw new IllegalStateException("Cannot finish a traversal with another unfinished one.");
                    }
                }

                getTraversedNodes().addAll(finishedTraversal.getTraversedNodes());

                return finishedTraversal instanceof Found f
                        ? new Found(getTraversedNodes(), f.getFoundNode())
                        : new NotFound(getTraversedNodes());
            }
        }
    }
}
