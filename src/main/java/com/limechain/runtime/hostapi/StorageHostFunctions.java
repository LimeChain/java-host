package com.limechain.runtime.hostapi;

import com.limechain.exception.scale.ScaleEncodingException;
import com.limechain.exception.trie.TrieTransactionException;
import com.limechain.runtime.SharedMemory;
import com.limechain.runtime.hostapi.dto.RuntimePointerSize;
import com.limechain.runtime.version.StateVersion;
import com.limechain.storage.DeleteByPrefixResult;
import com.limechain.trie.TrieAccessor;
import com.limechain.trie.structure.nibble.Nibbles;
import com.limechain.trie.structure.nibble.NibblesUtils;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.CompactMode;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.lang.Nullable;
import org.wasmer.ImportObject;
import org.wasmer.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.limechain.runtime.hostapi.PartialHostApi.newImportObjectPair;

/**
 * Implementations of the Storage HostAPI functions
 * For more info check
 * {<a href="https://spec.polkadot.network/chap-host-api#sect-storage-api">Storage API</a>}
 */
@Log
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class StorageHostFunctions implements PartialHostApi {
    public static final String TRANSACTION_PANIC = "No active transaction.";

    private final SharedMemory sharedMemory;
    private final TrieAccessor trieAccessor;

    public static byte[] scaleEncodedOption(int data) {
        return ScaleUtils.Encode.encodeOptional(ScaleCodecWriter::writeUint32, data);
    }

    public static byte[] scaleEncodedOption(@Nullable byte[] data) {
        return ScaleUtils.Encode.encodeOptional(ScaleCodecWriter::writeAsList, data);
    }

    @Override
    public Map<Endpoint, ImportObject.FuncImport> getFunctionImports() {
        return Map.ofEntries(
                newImportObjectPair(Endpoint.ext_storage_set_version_1, argv -> {
                    extStorageSetVersion1(
                            new RuntimePointerSize(argv.get(0)),
                            new RuntimePointerSize(argv.get(1)));
                }),
                newImportObjectPair(Endpoint.ext_storage_get_version_1, argv -> {
                    return extStorageGetVersion1(new RuntimePointerSize(argv.get(0))).pointerSize();
                }),
                newImportObjectPair(Endpoint.ext_storage_read_version_1, argv -> {
                    return extStorageReadVersion1(
                            new RuntimePointerSize(argv.get(0)), new RuntimePointerSize(argv.get(1)),
                            argv.get(2).intValue()).pointerSize();
                }),
                newImportObjectPair(Endpoint.ext_storage_clear_version_1, argv -> {
                    extStorageClearVersion1(new RuntimePointerSize(argv.get(0)));
                }),

                newImportObjectPair(Endpoint.ext_storage_exists_version_1, argv -> {
                    return extStorageExistsVersion1(new RuntimePointerSize(argv.get(0)));
                }),

                newImportObjectPair(Endpoint.ext_storage_clear_prefix_version_1, argv -> {
                    extStorageClearPrefixVersion1(new RuntimePointerSize(argv.get(0)));
                }),

                newImportObjectPair(Endpoint.ext_storage_clear_prefix_version_2, argv -> {
                    return extStorageClearPrefixVersion2(
                            new RuntimePointerSize(argv.get(0)),
                            new RuntimePointerSize(argv.get(1))
                    ).pointerSize();

                }),
                newImportObjectPair(Endpoint.ext_storage_append_version_1, argv -> {
                    extStorageAppendVersion1(
                            new RuntimePointerSize(argv.get(0)), new RuntimePointerSize(argv.get(1))
                    );
                }),

                newImportObjectPair(Endpoint.ext_storage_root_version_1, argv -> {
                    return extStorageRootVersion1().pointerSize();
                }),

                newImportObjectPair(Endpoint.ext_storage_root_version_2, argv -> {
                    return extStorageRootVersion2(argv.get(0).intValue()).pointerSize();
                }),
                newImportObjectPair(Endpoint.ext_storage_changes_root_version_1, argv -> {
                    return extStorageChangesRootVersion1(new RuntimePointerSize(argv.get(0))).pointerSize();
                }),
                newImportObjectPair(Endpoint.ext_storage_next_key_version_1, argv -> {
                    return extStorageNextKeyVersion1(new RuntimePointerSize(argv.get(0))).pointerSize();
                }),
                newImportObjectPair(Endpoint.ext_storage_start_transaction_version_1, argv -> {
                    extStorageStartTransactionVersion1();
                }),
                newImportObjectPair(Endpoint.ext_storage_rollback_transaction_version_1, argv -> {
                    extStorageRollbackTransactionVersion1();
                }),
                newImportObjectPair(Endpoint.ext_storage_commit_transaction_version_1, argv -> {
                    extStorageCommitTransactionVersion1();
                })
        );
    }

    /**
     * Sets the value under a given key into storage.
     *
     * @param keyPointer   a pointer-size containing the key.
     * @param valuePointer a pointer-size containing the key.
     */
    public void extStorageSetVersion1(RuntimePointerSize keyPointer, RuntimePointerSize valuePointer) {
        Nibbles key = Nibbles.fromBytes(sharedMemory.readData(keyPointer));
        byte[] value = sharedMemory.readData(valuePointer);

        log.fine("");
        log.fine("extStorageSetVersion1 with ");
        log.fine("key: " + key);
        log.fine("value: " + Arrays.toString(value));
        log.fine("");
        trieAccessor.upsertNode(key, value);
    }

    /**
     * Retrieves the value associated with the given key from storage.
     *
     * @param keyPointer a pointer-size containing the key
     * @return a pointer-size returning the SCALE encoded Option value containing the value.
     */
    public RuntimePointerSize extStorageGetVersion1(RuntimePointerSize keyPointer) {
        Nibbles key = Nibbles.fromBytes(sharedMemory.readData(keyPointer));
        byte[] value = trieAccessor.findStorageValue(key).orElse(null);

        log.fine("");
        log.fine("extStorageGetVersion1");
        log.fine("key: " + key);
        log.fine("");

        return sharedMemory.writeData(scaleEncodedOption(value));
    }

    /**
     * Gets the given key from storage, placing the value into a buffer and returning the number of bytes
     * that the entry in storage has beyond the offset.
     *
     * @param keyPointer      a pointer-size containing the key.
     * @param valueOutPointer a pointer-size containing the buffer to which the value will be written to.
     *                        This function will never write more than the length of the buffer,
     *                        even if the value’s length is bigger.
     * @param offset          the offset beyond the value that should be read from.
     * @return a pointer-size pointing to a SCALE encoded Option value containing an unsigned 32-bit integer
     * representing the number of bytes left at supplied offset. Returns None if the entry does not exist.
     */
    public RuntimePointerSize extStorageReadVersion1(RuntimePointerSize keyPointer, RuntimePointerSize valueOutPointer,
                                                     int offset) {
        log.fine("extStorageReadVersion1");
        Nibbles key = Nibbles.fromBytes(sharedMemory.readData(keyPointer));
        byte[] value = trieAccessor.findStorageValue(key).orElse(null);

        if (value == null) {
            return sharedMemory.writeData(scaleEncodedOption(null));
        }

        int size = 0;
        if (offset <= value.length) {
            size = value.length - offset;
            byte[] data = Arrays.copyOfRange(value, offset, value.length);

            sharedMemory.writeData(data, valueOutPointer);
        }

        return sharedMemory.writeData(scaleEncodedOption(size));
    }

    /**
     * Clears the storage of the given key and its value. Non-existent entries are silently ignored.
     *
     * @param keyPointer a pointer-size containing the key.
     */
    public void extStorageClearVersion1(RuntimePointerSize keyPointer) {
        Nibbles key = Nibbles.fromBytes(sharedMemory.readData(keyPointer));

        log.fine("");
        log.fine("extStorageClearVersion1");
        log.fine("key: " + key);
        log.fine("");

        trieAccessor.deleteNode(key);
    }

    /**
     * Checks whether the given key exists in storage.
     *
     * @param keyPointer a pointer-size containing the key.
     * @return integer value equal to 1 if the key exists or a value equal to 0 if otherwise.
     */
    public int extStorageExistsVersion1(RuntimePointerSize keyPointer) {
        log.fine("extStorageExistsVersion1");
        Nibbles key = Nibbles.fromBytes(sharedMemory.readData(keyPointer));
        return trieAccessor.findStorageValue(key).isPresent() ? 1 : 0;
    }

    /**
     * Clear the storage of each key/value pair where the key starts with the given prefix.
     *
     * @param prefixPointer a pointer-size containing the prefix.
     */
    public void extStorageClearPrefixVersion1(RuntimePointerSize prefixPointer) {
        log.fine("extStorageClearPrefixVersion1");
        Nibbles prefix = Nibbles.fromBytes(sharedMemory.readData(prefixPointer));
        trieAccessor.deleteMultipleNodesByPrefix(prefix, null);
    }

    /**
     * Clear the storage of each key/value pair where the key starts with the given prefix.
     *
     * @param prefixPointer a pointer-size containing the prefix.
     * @param limitPointer  a pointer-size to an Option type containing an unsigned 32-bit integer indicating the limit
     *                      on how many keys should be deleted. No limit is applied if this is None.
     *                      Any keys created during the current block execution do not count toward the limit.
     * @return a pointer-size to the following variant, k = 0 -> c | k = 1 -> c
     * where 0 indicates that all keys of the child storage have been removed, followed by the number of removed keys.
     * The variant 1 indicates that there are remaining keys, followed by the number of removed keys.
     */
    public RuntimePointerSize extStorageClearPrefixVersion2(RuntimePointerSize prefixPointer,
                                                            RuntimePointerSize limitPointer) {
        log.fine("extStorageClearPrefixVersion2");
        Nibbles prefix = Nibbles.fromBytes(sharedMemory.readData(prefixPointer));

        byte[] limitBytes = sharedMemory.readData(limitPointer);
        Long limit = new ScaleCodecReader(limitBytes).readOptional(ScaleCodecReader.UINT32).orElse(null);

        DeleteByPrefixResult result = trieAccessor.deleteMultipleNodesByPrefix(prefix, limit);

        return sharedMemory.writeData(result.scaleEncoded());
    }

    /**
     * Append the SCALE encoded value to a SCALE encoded sequence at the given key.
     * This function assumes that the existing storage item is either empty or a SCALE-encoded sequence and that
     * the value to append is also SCALE encoded and of the same type as the items in the existing sequence.
     *
     * @param keyPointer   a pointer-size containing the key.
     * @param valuePointer a pointer-size containing the value to be appended.
     */
    public void extStorageAppendVersion1(RuntimePointerSize keyPointer, RuntimePointerSize valuePointer) {
        log.fine("extStorageAppendVersion1");

        Nibbles key = Nibbles.fromBytes(sharedMemory.readData(keyPointer));
        byte[] sequence = trieAccessor.findStorageValue(key).orElse(null);
        byte[] valueToAppend = sharedMemory.readData(valuePointer);

        if (sequence == null) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (ScaleCodecWriter writer = new ScaleCodecWriter(buf)) {
                writer.writeCompact(1);
                writer.writeByteArray(valueToAppend);
            } catch (IOException e) {
                throw new ScaleEncodingException(e);
            }
            trieAccessor.upsertNode(key, buf.toByteArray());
            return;
        }

        int numberOfItems;
        try {
            numberOfItems = new ScaleCodecReader(sequence).readCompactInt();
        } catch (IndexOutOfBoundsException e) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (ScaleCodecWriter writer = new ScaleCodecWriter(buf)) {
                writer.writeCompact(1);
                writer.writeByteArray(valueToAppend);
            } catch (IOException ez) {
                throw new ScaleEncodingException(e);
            }
            trieAccessor.upsertNode(key, buf.toByteArray());
            return;
        }

        int numberOfScaleLengthBytes = switch (CompactMode.forNumber(numberOfItems)) {
            case SINGLE -> 1;
            case TWO -> 2;
            default -> 4;
        };

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ScaleCodecWriter writer = new ScaleCodecWriter(buf)) {
            writer.writeCompact(numberOfItems + 1);
            writer.writeByteArray(Arrays.copyOfRange(sequence, numberOfScaleLengthBytes, sequence.length));
            writer.writeByteArray(valueToAppend);
        } catch (IOException e) {
            throw new ScaleEncodingException(e);
        }
        trieAccessor.upsertNode(key, buf.toByteArray());
    }

    /**
     * Compute the storage root.
     *
     * @return a pointer-size to a buffer containing the 256-bit Blake2 storage root.
     */
    public RuntimePointerSize extStorageRootVersion1() {
        log.fine("extStorageRootVersion1");
        byte[] rootHash = trieAccessor.getMerkleRoot(null);

        return sharedMemory.writeData(rootHash);
    }

    /**
     * Compute the storage root.
     *
     * @param version the state version
     * @return a pointer-size to a buffer containing the 256-bit Blake2 storage root.
     */
    public RuntimePointerSize extStorageRootVersion2(int version) {
        log.fine("extStorageRootVersion2");
        byte[] rootHash = trieAccessor.getMerkleRoot(StateVersion.fromInt(version));

        return sharedMemory.writeData(rootHash);
    }

    /**
     * This function is no longer used and only exists for compatibility reasons.
     *
     * @param parentHashPointer a pointer-size to the SCALE encoded block hash.
     * @return a pointer-size to an Option type (Definition 185) that’s always None.
     */
    public RuntimePointerSize extStorageChangesRootVersion1(RuntimePointerSize parentHashPointer) {
        log.fine("extStorageChangesRootVersion1");

        return sharedMemory.writeData(scaleEncodedOption(null));
    }

    /**
     * Get the next key in storage after the given one in lexicographic order.
     * The key provided to this function may or may not exist in storage.
     *
     * @param keyPointer a pointer-size to the key.
     * @return a pointer-size to the SCALE encoded Option value containing the next key in lexicographic order.
     */
    public RuntimePointerSize extStorageNextKeyVersion1(RuntimePointerSize keyPointer) {
        Nibbles key = Nibbles.fromBytes(sharedMemory.readData(keyPointer));

        log.fine("");
        log.fine("extStorageNextKeyVersion1");
        log.fine("key: " + key);
        log.fine("");

        byte[] nextKey = trieAccessor.getNextKey(key)
                .map(NibblesUtils::toBytesAppending)
                .map(this::asByteArray)
                .orElse(null);

        return sharedMemory.writeData(scaleEncodedOption(nextKey));
    }

    private byte[] asByteArray(List<Byte> bytes) {
        byte[] result = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            result[i] = bytes.get(i);
        }
        return result;
    }

    /**
     * Start a new nested transaction. This allows to either commit or roll back all changes that are made after this
     * call. For every transaction, there must be a matching call to either ext_storage_rollback_transaction or
     * ext_storage_commit_transaction. This is also effective for all values manipulated using the child storage API.
     * It’s legal to call this function multiple times in a row.
     */
    public void extStorageStartTransactionVersion1() {
        log.fine("extStorageStartTransactionVersion1");
        trieAccessor.startTransaction();
    }

    /**
     * Rollback the last transaction started by ext_storage_start_transaction.
     * Any changes made during that transaction are discarded. It’s legal to call this function multiple times in a row.
     */
    public void extStorageRollbackTransactionVersion1() {
        log.fine("extStorageRollbackTransactionVersion1");
        try {
            trieAccessor.rollbackTransaction();
        } catch (TrieTransactionException e) {
            Util.nativePanic(TRANSACTION_PANIC);
        }
    }

    /**
     * Commit the last transaction started by ext_storage_start_transaction.
     * Any changes made during that transaction are committed to the main state.
     * It’s legal to call this function multiple times in a row.
     */
    public void extStorageCommitTransactionVersion1() {
        log.fine("extStorageCommitTransactionVersion1");
        try {
            trieAccessor.commitTransaction();
        } catch (TrieTransactionException e) {
            Util.nativePanic(TRANSACTION_PANIC);
        }
    }
}
