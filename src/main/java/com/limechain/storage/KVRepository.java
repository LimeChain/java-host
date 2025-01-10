package com.limechain.storage;

import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Key-Value DB Interface
 *
 * @param <K> type of the key
 * @param <V> type of the value
 */
public interface KVRepository<K, V> {
    /**
     * Persists a key-value pair to the DB
     *
     * @param key   key of the pair
     * @param value value of the pair
     * @return whether the save operation was successful
     */
    boolean save(K key, V value);

    /**
     * @param kvMap a map of all the key value pairs
     */
    void saveBatch(Map<K, V> kvMap);

    /**
     * Tries to find a value for a given key in the DB
     *
     * @param key the key to search for
     * @return Optional result that could contain the value
     */
    Optional<V> find(K key);


    /**
     * Generic method to fetch a value from the repository with a default fallback if no result is being found.
     *
     * @param key          The key to fetch from the repository.
     * @param defaultValue The default value to return if the key is not found.
     * @param <T>          The type of the value being fetched.
     * @return The fetched value or the default if not present.
     */
    <T> T find(String key, T defaultValue);

    /**
     * Deletes a key-value pair from the DB
     *
     * @param key the key of the pair
     * @return whether the delete operation was successful
     */
    boolean delete(K key);

    /**
     * Finds all keys sharing a common prefix up to a given limit.
     *
     * @param prefixSeek prefix of the key to look for
     * @param limit maximum keys to return
     * @return whether the delete operation was successful
     */
    List<byte[]> findKeysByPrefix(K prefixSeek, int limit);

    /**
     * Deletes key-value pairs from the DB where key starts with prefix, up to a given limit.
     *
     * @param prefix prefix for the key of the pair
     * @param limit maximum entries to delete
     * @return how many entries were deleted and if all were deleted
     */
    DeleteByPrefixResult deleteByPrefix(K prefix, @Nullable Long limit);

    /**
     * Tries to find the next key after a given key in the DB
     *
     * @param key the key to search for
     * @return Optional result that could contain the value
     */
    Optional<K> getNextKey(K key);

    /**
     * Closes the connection to the DB
     */
    void closeConnection();
}

