package com.limechain.sync.fullsync;

import com.limechain.storage.DeleteByPrefixResult;
import com.limechain.storage.KVRepository;
import org.apache.commons.collections4.trie.PatriciaTrie;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An in-memory implementation of {@link KVRepository} for testing purposes.
 */
class InMemoryDB implements KVRepository<String, Object> {
    private final PatriciaTrie<Object> storage = new PatriciaTrie<>();

    @Override
    public boolean save(String key, Object value) {
        storage.put(key, value);
        return true;
    }

    @Override
    public void saveBatch(Map<String, Object> stringObjectMap) {
        storage.putAll(stringObjectMap);
    }

    @Override
    public Optional<Object> find(String key) {
        return Optional.ofNullable(storage.get(key));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T find(String key, T defaultValue) {
        return (T) find(key).orElse(defaultValue);
    }

    @Override
    public boolean delete(String key) {
        storage.remove(key);
        return true;
    }

    @Override
    public List<byte[]> findKeysByPrefix(String prefixSeek, int limit) {
        return storage.prefixMap(prefixSeek).keySet().stream().map(String::getBytes).limit(limit).toList();
    }

    @Override
    public DeleteByPrefixResult deleteByPrefix(String prefix, Long limit) {
        var keys = this.findKeysByPrefix(prefix, Math.toIntExact(limit));

        int deleted = 0;
        for (var key : keys) {
            storage.remove(new String(key));
            deleted++;
        }

        boolean allDeleted = this.findKeysByPrefix(prefix, Math.toIntExact(limit)).isEmpty();

        return new DeleteByPrefixResult(deleted, allDeleted);
    }

    @Override
    public Optional<String> getNextKey(String key) {
        return Optional.empty();
    }

    @Override
    public void closeConnection() {

    }
}
