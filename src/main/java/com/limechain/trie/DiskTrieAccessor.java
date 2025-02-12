package com.limechain.trie;

import com.limechain.runtime.version.StateVersion;
import com.limechain.storage.DeleteByPrefixResult;
import com.limechain.storage.trie.TrieStorage;
import com.limechain.trie.structure.nibble.Nibbles;

import java.util.Optional;

public sealed class DiskTrieAccessor extends TrieAccessor permits DiskChildTrieAccessor {

    private DiskTrieService diskTrieService;
    // Serves as a temporary backup if a runtime call other than "Core_execute_block" alters state.
    private DiskTrieService backupTrieService;

    public DiskTrieAccessor(TrieStorage trieStorage, byte[] mainTrieRoot) {
        super(trieStorage, mainTrieRoot);

        this.diskTrieService = new DiskTrieService(trieStorage, mainTrieRoot);
    }

    /**
     * A constructor used to create a deep copy of a {@link TrieAccessor}.
     *
     * @param original the instance to copy
     */
    public DiskTrieAccessor(TrieAccessor original) {
        super(original.trieStorage, original.mainTrieRoot.clone());

        this.diskTrieService = new DiskTrieService(((DiskTrieAccessor) original).diskTrieService);
        this.currentStateVersion = original.currentStateVersion;
    }

    @Override
    public void upsertNode(Nibbles key, byte[] value) {
        diskTrieService.upsertNode(key, value, currentStateVersion);
    }

    @Override
    public void deleteNode(Nibbles key) {
        diskTrieService.deleteStorageNode(key);
    }

    @Override
    public Optional<byte[]> findStorageValue(Nibbles key) {
        return diskTrieService.findStorageValue(key);
    }

    @Override
    public DeleteByPrefixResult deleteMultipleNodesByPrefix(Nibbles prefix, Long limit) {
        return diskTrieService.deleteMultipleNodesByPrefix(prefix, limit);
    }

    @Override
    public Optional<Nibbles> getNextKey(Nibbles key) {
        return diskTrieService.getNextKey(key);
    }

    @Override
    public void persistChanges() {
        super.persistChanges();
        diskTrieService.persistChanges();
    }

    @Override
    public DiskChildTrieAccessor getChildTrie(Nibbles key) {
        return (DiskChildTrieAccessor) super.getChildTrie(key);
    }

    @Override
    public DiskChildTrieAccessor createChildTrie(Nibbles trieKey, byte[] merkleRoot) {
        return new DiskChildTrieAccessor(trieStorage, this, trieKey, merkleRoot);
    }

    @Override
    public void prepareBackup() {
        backupTrieService = new DiskTrieService(diskTrieService);
    }

    @Override
    public void backup() {
        diskTrieService = backupTrieService;
        backupTrieService = null;
    }

    @Override
    public byte[] getMerkleRoot(StateVersion version) {
        if (version != null && !currentStateVersion.equals(version)) {
            throw new IllegalStateException("Trie state version must match runtime call one.");
        }
        return diskTrieService.getMerkleRoot();
    }

    @Override
    public void startTransaction() {
        loadedChildTries.forEach((_, value) -> value.startTransaction());
        diskTrieService.startTransaction();
    }

    @Override
    public void rollbackTransaction() {
        loadedChildTries.forEach((_, value) -> value.rollbackTransaction());
        diskTrieService.rollbackTransaction();
    }

    @Override
    public void commitTransaction() {
        loadedChildTries.forEach((_, value) -> value.commitTransaction());
        diskTrieService.commitTransaction();
    }
}
