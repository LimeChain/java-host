package com.limechain.storage;

import com.limechain.exception.storage.DBException;
import com.limechain.trie.structure.nibble.Nibbles;
import com.limechain.utils.ByteArrayUtils;
import lombok.extern.java.Log;
import org.apache.commons.io.FileUtils;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.springframework.util.SerializationUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Implementation for Key-Value DB interface with String as key and Object as value types
 */
@Log
public class DBRepository implements KVRepository<String, Object> {
    /**
     * Main DB folder
     */
    private static final String FOLDER_NAME = "db";

    /**
     * Connection to the DB
     */
    private RocksDB db;

    public DBRepository(String path, String chain, boolean dbRecreate) {
        RocksDB.loadLibrary();
        final Options options = new Options();
        options.setCreateIfMissing(true);
        File baseDir = Path.of(path, FOLDER_NAME, chain).toFile();
        if (dbRecreate) {
            cleanDatabaseFolder(baseDir);
        }
        try {
            Files.createDirectories(baseDir.getParentFile().toPath());
            Files.createDirectories(baseDir.getAbsoluteFile().toPath());
            db = RocksDB.open(options, baseDir.getAbsolutePath());
            log.log(Level.INFO, "\uD83E\uDEA8RocksDB initialized");
        } catch (IOException | RocksDBException e) {
            log.log(Level.SEVERE, String.format("Error initializing RocksDB. Exception: '%s', message: '%s'",
                    e.getCause(),
                    e.getMessage()),
                e);
        }
    }

    private void cleanDatabaseFolder(File file) {
        try {
            if (file.exists()) {
                FileUtils.cleanDirectory(file.getAbsoluteFile());
                log.log(Level.INFO, "\uD83D\uDDD1️DB cleaned");
            }

        } catch (IOException e) {
            log.log(Level.SEVERE, String.format("Error deleting db folder. Exception: '%s', message: '%s'",
                    e.getCause(),
                    e.getMessage()),
                e);
            throw new DBException(e);
        }
    }

    @Override
    public synchronized void saveBatch(Map<String, Object> kvMap) {
        log.fine("Saving batch of key value pairs.");
        try (final WriteBatch batch = new WriteBatch()) {
            for (Map.Entry<String, Object> e : kvMap.entrySet()) {
                batch.put(e.getKey().getBytes(UTF_8), SerializationUtils.serialize(e.getValue()));
            }

            try (final WriteOptions writeOptions = new WriteOptions()) {
                db.write(writeOptions, batch);
            }
        } catch (RocksDBException e) {
            log.warning(String.format("Error saving batch. Cause: '%s', message: '%s'", e.getCause(), e.getMessage()));
        }
    }

    @Override
    public synchronized boolean save(String key, Object value) {
        log.log(Level.FINE, String.format("saving value '%s' with key '%s'", value, key));
        try {
            db.put(key.getBytes(UTF_8), SerializationUtils.serialize(value));
        } catch (RocksDBException e) {
            log.log(Level.WARNING,
                String.format("Error saving entry. Cause: '%s', message: '%s'", e.getCause(), e.getMessage()));
            return false;
        }
        return true;
    }

    @Override
    public synchronized Optional<Object> find(String key) {
        Object value = null;
        try {
            byte[] bytes = db.get(key.getBytes(UTF_8));
            if (bytes != null) {
                value = SerializationUtils.deserialize(bytes);
            }
        } catch (RocksDBException e) {
            log.severe(String.format(
                "Error retrieving the entry with key: %s, cause: %s, message: %s",
                key,
                e.getCause(),
                e.getMessage())
            );
        }
        log.fine(String.format("finding key '%s' returns '%s'", Nibbles.fromBytes(key.getBytes()), value));
        return Optional.ofNullable(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T find(String key, T defaultValue) {
        return (T) find(key).orElse(defaultValue);
    }

    @Override
    public synchronized List<byte[]> findKeysByPrefix(String prefixSeek, int limit) {
        return findByPrefix(prefixSeek, (long) limit)
            .stream()
            .toList();
    }

    @Override
    public synchronized boolean delete(String key) {
        log.log(Level.FINE, String.format("deleting key '%s'", key));
        try {
            db.delete(key.getBytes(UTF_8));
        } catch (RocksDBException e) {
            log.log(Level.SEVERE,
                String.format("Error deleting entry, cause: '%s', message: '%s'", e.getCause(), e.getMessage()));
            return false;
        }
        return true;
    }

    @Override
    public synchronized DeleteByPrefixResult deleteByPrefix(String prefix, Long limit) {
        log.log(Level.FINE, String.format("deleting %s keys with prefix '%s'", limit == null ? "all" : limit, prefix));
        List<byte[]> keysToDelete = findByPrefix(prefix, limit);

        keysToDelete.forEach(key -> {
            try {
                db.delete(key);
            } catch (RocksDBException e) {
                log.log(Level.SEVERE, String.format("Error deleting entry, cause: '%s', message: '%s'",
                    e.getCause(), e.getMessage()));
            }
        });

        boolean allDeleted = findByPrefix(prefix, 1L).isEmpty();

        return new DeleteByPrefixResult(keysToDelete.size(), allDeleted);
    }

    private List<byte[]> findByPrefix(String prefix, Long limit) {
        List<byte[]> values = new ArrayList<>();
        RocksIterator rocksIterator = db.newIterator();
        rocksIterator.seek(prefix.getBytes());
        while (rocksIterator.isValid() && (limit == null || values.size() < limit)) {
            byte[] key = rocksIterator.key();
            if (ByteArrayUtils.hasPrefix(key, prefix.getBytes())) {
                values.add(rocksIterator.key());
            }
            rocksIterator.next();
        }
        rocksIterator.close();

        return values;
    }

    @Override
    public synchronized Optional<String> getNextKey(String key) {
        RocksIterator iterator = db.newIterator();
        iterator.seek(key.getBytes(UTF_8));
        iterator.next();
        String nextKey = iterator.isValid() ? new String(iterator.key()) : null;
        iterator.close();
        return Optional.ofNullable(nextKey);
    }

    public synchronized void closeConnection() {
        this.db.close();
    }

}
