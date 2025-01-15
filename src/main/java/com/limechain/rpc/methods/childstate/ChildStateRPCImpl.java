package com.limechain.rpc.methods.childstate;

import com.limechain.storage.block.state.BlockState;
import com.limechain.storage.trie.TrieStorage;
import com.limechain.trie.structure.database.NodeData;
import com.limechain.trie.structure.nibble.Nibbles;
import com.limechain.utils.StringUtils;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChildStateRPCImpl {

    private final TrieStorage trieStorage;
    private final BlockState blockState;

    /**
     * Retrieves a list of storage keys that match a given prefix within a specific child storage and block.
     *
     * @param prefixHex    The prefix (in hexadecimal format) to match the keys against.
     * @param childKeyHex  The hexadecimal representation of the child storage key.
     * @param blockHashHex The block hash (in hexadecimal format) to scope the search.
     * @return A list of matching keys in hexadecimal format. Returns an empty list if the block state is not initialized.
     */
    public List<String> childStateGetKeys(String prefixHex, String childKeyHex, String blockHashHex) {
        if (!this.blockState.isInitialized()) {
            return Collections.emptyList();
        }

        Nibbles prefix = Nibbles.fromHexString(prefixHex);
        byte[] childStorageMerkle = getChildStorageMerkle(childKeyHex, blockHashHex);

        return trieStorage
                .getKeysWithPrefix(childStorageMerkle, prefix)
                .stream()
                .map(key -> StringUtils.HEX_PREFIX + key.toLowerHexString())
                .toList();
    }

    /**
     * Retrieves the storage value for a specific key within a child storage and block.
     *
     * @param childKeyHex  The hexadecimal representation of the child storage key.
     * @param keyHex       The key (in hexadecimal format) for which to retrieve the storage value.
     * @param blockHashHex The block hash (in hexadecimal format) to scope the search.
     * @return The storage value in hexadecimal format,
     * or {@code null} if the block state is not initialized or the value does not exist.
     */
    public String stateGetStorage(String childKeyHex, String keyHex, String blockHashHex) {
        if (!this.blockState.isInitialized()) {
            return null;
        }

        byte[] childStorageMerkle = getChildStorageMerkle(childKeyHex, blockHashHex);

        return trieStorage.getByKeyFromMerkle(childStorageMerkle, Nibbles.fromHexString(keyHex))
                .map(NodeData::getValue)
                .map(StringUtils::toHexWithPrefix)
                .orElse(null);
    }

    /**
     * Retrieves the storage hash for a specific key within a child storage and block.
     *
     * @param childKeyHex  The hexadecimal representation of the child storage key.
     * @param keyHex       The key (in hexadecimal format) for which to retrieve the storage hash.
     * @param blockHashHex The block hash (in hexadecimal format) to scope the search.
     * @return The storage hash in hexadecimal format, or {@code null} if the block state is not initialized or the hash does not exist.
     */
    public String stateGetStorageHash(String childKeyHex, String keyHex, String blockHashHex) {
        if (!this.blockState.isInitialized()) {
            return null;
        }

        byte[] childStorageMerkle = getChildStorageMerkle(childKeyHex, blockHashHex);

        return trieStorage.getByKeyFromMerkle(childStorageMerkle, Nibbles.fromHexString(keyHex))
                .map(NodeData::getMerkleValue)
                .map(StringUtils::toHexWithPrefix)
                .orElse(null);
    }

    /**
     * Retrieves the storage size for a specific key within a child storage and block.
     *
     * @param childKeyHex  The hexadecimal representation of the child storage key.
     * @param keyHex       The key (in hexadecimal format) for which to retrieve the storage size.
     * @param blockHashHex The block hash (in hexadecimal format) to scope the search.
     * @return The storage size as a string, or {@code null} if the block state is not initialized or the key does not exist.
     */
    public String stateGetStorageSize(String childKeyHex, String keyHex, String blockHashHex) {
        if (!this.blockState.isInitialized()) {
            return null;
        }

        byte[] childStorageMerkle = getChildStorageMerkle(childKeyHex, blockHashHex);

        return trieStorage
                .getByKeyFromMerkle(childStorageMerkle, Nibbles.fromHexString(keyHex))
                .map(NodeData::getValue)
                .map(Array::getLength)
                .map(String::valueOf)
                .orElse(null);
    }

    private Hash256 getHash256FromHex(String blockHashHex) {
        return blockHashHex != null ? Hash256.from(blockHashHex) : blockState.getHighestFinalizedHash();
    }

    private byte[] getChildStorageMerkle(String childKeyHex, String blockHashHex) {
        Nibbles childKey = Nibbles.fromHexString(childKeyHex);
        final Hash256 blockHash = getHash256FromHex(blockHashHex);

        return getChildMerkle(blockHash, childKey);
    }

    private byte[] getChildMerkle(Hash256 blockHash, Nibbles childKey) {
        Hash256 mainTrieMerkle = blockState.getBlockStateRoot(blockHash);
        return trieStorage
                .getByKeyFromMerkle(mainTrieMerkle.getBytes(), Nibbles.fromBytes(":child_storage:default:".getBytes(StandardCharsets.US_ASCII)).addAll(childKey))
                .map(NodeData::getValue)
                .orElse(null);
    }
}
