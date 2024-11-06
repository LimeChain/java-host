package com.limechain.trie;

import com.limechain.runtime.version.StateVersion;
import com.limechain.storage.DeleteByPrefixResult;
import com.limechain.storage.trie.TrieStorage;
import com.limechain.trie.structure.nibble.Nibbles;

import java.util.Optional;

public sealed class DiskTrieAccessor extends TrieAccessor permits DiskChildTrieAccessor {

    private final DiskTrieService diskTrieService;

    public DiskTrieAccessor(TrieStorage trieStorage, byte[] mainTrieRoot) {
        super(trieStorage, mainTrieRoot);

        this.diskTrieService = new DiskTrieService(trieStorage, mainTrieRoot);
    }

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

    public byte[] getMerkleRoot(StateVersion version) {
        if (version != null && !currentStateVersion.equals(version)) {
            throw new IllegalStateException("Trie state version must match runtime call one.");
        }
        return diskTrieService.getMerkleRoot();
    }
}
