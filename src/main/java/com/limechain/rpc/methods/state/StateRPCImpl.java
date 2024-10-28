package com.limechain.rpc.methods.state;

import com.limechain.rpc.methods.state.dto.StorageChangeSet;
import com.limechain.runtime.Runtime;
import com.limechain.storage.block.BlockState;
import com.limechain.storage.trie.TrieStorage;
import com.limechain.trie.dto.node.StorageNode;
import com.limechain.trie.structure.database.NodeData;
import com.limechain.trie.structure.nibble.Nibbles;
import com.limechain.utils.StringUtils;
import io.emeraldpay.polkaj.types.Hash256;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class StateRPCImpl {

    @Autowired
    private TrieStorage trieStorage;

    private final BlockState blockState = BlockState.getInstance();

    /**
     * Placeholder for future API implementation. Currently throws {@link UnsupportedOperationException}.
     *
     * @param method       The method name to call.
     * @param data         The data to be passed to the method.
     * @param blockHashHex The hexadecimal representation of the block hash.
     */
    public void stateCall(final String method, final String data, final String blockHashHex) {
        throw new UnsupportedOperationException("This API is future-reserved.");
    }

    /**
     * Retrieves key-value pairs from storage, filtered by a prefix and scoped to a specific block.
     *
     * @param prefixHex    The prefix in hexadecimal format used to filter the keys.
     * @param blockHashHex The block hash in hexadecimal format to scope the query.
     * @return An array of key-value pairs matching the given prefix within the specified block.
     */
    public String[][] stateGetPairs(final String prefixHex, final String blockHashHex) {
        if (!this.blockState.isInitialized()) {
            return new String[0][0];
        }

        final Hash256 blockStateRoot = blockState.getBlockStateRoot(getHash256FromHex(blockHashHex));
        final Nibbles prefix = Nibbles.fromHexString(prefixHex);

        Optional<StorageNode> optionalNextBranch = trieStorage.getNextBranch(blockStateRoot, prefix);
        if (optionalNextBranch.isEmpty()) {
            return new String[0][0];
        }
        StorageNode nextBranch = optionalNextBranch.get();
        byte[] nextBranchMerkle = nextBranch.nodeData().getMerkleValue();

        // FIXME: This seems to not traverse the entire trie, but a single level deep only
        return trieStorage
                .loadChildren(nextBranch.key(), nextBranchMerkle)
                .stream()
                .map(storageNode -> {
                    final String key = storageNode.key().toLowerHexString();
                    final String value = StringUtils.toHexWithPrefix(storageNode.nodeData().getValue());
                    return new String[]{key, value};
                })
                .toArray(String[][]::new);
    }

    /**
     * Retrieves a paginated list of storage keys, filtered by a prefix, starting from a specific key, and scoped to a specific block.
     *
     * @param prefixHex    The prefix in hexadecimal format used to filter the keys.
     * @param limit        The maximum number of keys to return.
     * @param keyHex       The starting key in hexadecimal format for pagination.
     * @param blockHashHex The block hash in hexadecimal format to scope the query.
     * @return A list of storage keys matching the given prefix within the specified block, subject to pagination.
     */
    public List<String> stateGetKeysPaged(final String prefixHex, int limit, String keyHex, final String blockHashHex) {
        if (!this.blockState.isInitialized()) {
            return Collections.emptyList();
        }

        Nibbles prefix = prefixHex != null ? Nibbles.fromHexString(prefixHex) : Nibbles.EMPTY;
        Nibbles startKey = keyHex != null ? Nibbles.fromHexString(keyHex) : Nibbles.EMPTY;
        final Hash256 blockStateRoot = blockState.getBlockStateRoot(getHash256FromHex(blockHashHex));

        return trieStorage
                .getKeysWithPrefixPaged(blockStateRoot, prefix, startKey, limit)
                .stream()
                .map(key -> StringUtils.HEX_PREFIX + key.toLowerHexString())
                .toList();
    }

    /**
     * Retrieves the storage value for a specific key, scoped to a specific block.
     *
     * @param keyHex       The key in hexadecimal format for which the storage value is retrieved.
     * @param blockHashHex The block hash in hexadecimal format to scope the query.
     * @return The storage value in hexadecimal format for the given key within the specified block, or null if not found.
     */
    // TODO: Figure out whether incoming requests will only ask for state in finalised block (i.e. db persisted blocks)
    //  or also unfinalised blocks must be supported (i.e. these in BlockState.BlockTree)
    //  For now we only handle persisted blocks (because we only read from the DB through trieStorage)
    public String stateGetStorage(final String keyHex, final String blockHashHex) {
        if (!this.blockState.isInitialized()) {
            return null;
        }

        final Hash256 blockHash = getHash256FromHex(blockHashHex);
        byte[] blockStateRoot = blockState.getBlockStateRoot(blockHash).getBytes();

        return trieStorage.getByKeyFromMerkle(blockStateRoot, Nibbles.fromHexString(keyHex))
                .map(NodeData::getValue)
                .map(StringUtils::toHexWithPrefix)
                .orElse(null);
    }

    /**
     * Retrieves the storage hash for a specific key, scoped to a specific block.
     *
     * @param keyHex       The key in hexadecimal format for which the storage hash is retrieved.
     * @param blockHashHex The block hash in hexadecimal format to scope the query.
     * @return The storage hash in hexadecimal format for the given key within the specified block, or null if not found.
     */
    public String stateGetStorageHash(final String keyHex, final String blockHashHex) {
        if (!this.blockState.isInitialized()) {
            return null;
        }

        final Hash256 blockHash = getHash256FromHex(blockHashHex);
        byte[] blockStateRoot = blockState.getBlockStateRoot(blockHash).getBytes();

        return trieStorage.getByKeyFromMerkle(blockStateRoot, Nibbles.fromHexString(keyHex))
                .map(NodeData::getMerkleValue)
                .map(StringUtils::toHexWithPrefix)
                .orElse(null);
    }

    /**
     * Retrieves the size of the storage for a specific key, scoped to a specific block.
     *
     * @param keyHex       The key in hexadecimal format for which the storage size is calculated.
     * @param blockHashHex The block hash in hexadecimal format to scope the query.
     * @return The size of the storage value for the given key within the specified block, or null if not found.
     */
    public String stateGetStorageSize(final String keyHex, final String blockHashHex) {
        if (!this.blockState.isInitialized()) {
            return null;
        }

        final Hash256 blockHash = getHash256FromHex(blockHashHex);
        byte[] blockStateRoot = blockState.getBlockStateRoot(blockHash).getBytes();

        return trieStorage
                .getByKeyFromMerkle(blockStateRoot, Nibbles.fromHexString(keyHex))
                .map(NodeData::getValue)
                .map(Array::getLength)
                .map(String::valueOf)
                .orElse(null);
    }

    /**
     * Retrieves the runtime metadata, scoped to a specific block.
     *
     * @param blockHashHex The block hash in hexadecimal format to scope the query.
     * @return The runtime metadata in hexadecimal format for the specified block, or null if not found or not initialized.
     */
    public String stateGetMetadata(final String blockHashHex) {
        if (!this.blockState.isInitialized()) {
            return null;
        }

        final Hash256 blockHash = getHash256FromHex(blockHashHex);

        final Runtime runtime = blockState.getRuntime(blockHash);
        byte[] metadataBytes = runtime.getMetadata();

        return StringUtils.toHexWithPrefix(metadataBytes);
    }

    /**
     * Retrieves the runtime version information, scoped to a specific block.
     *
     * @param blockHashHex The block hash in hexadecimal format to scope the query.
     * @return The runtime version information for the specified block, or null if not found or not initialized.
     */
    public String stateGetRuntimeVersion(final String blockHashHex) {
        if (!this.blockState.isInitialized()) {
            return null;
        }

        final Hash256 blockHash = getHash256FromHex(blockHashHex);

        Runtime runtime = blockState.getRuntime(blockHash);
        if (runtime != null) {
            return runtime.getCachedVersion().toString();
        }
        return null;
    }

    /**
     * Queries storage for changes to specified keys across a range of blocks.
     *
     * @param keysHex       A list of keys in hexadecimal format to query for changes.
     * @param startBlockHex The start block hash in hexadecimal format for the query range.
     * @param endBlockHex   The end block hash in hexadecimal format for the query range.
     * @return A list of {@link StorageChangeSet} representing the changes to the specified keys across the specified block range.
     */
    public List<StorageChangeSet> stateQueryStorage(final List<String> keysHex, final String startBlockHex,
                                                    final String endBlockHex) {
        if (!this.blockState.isInitialized()) {
            return Collections.emptyList();
        }

        final Hash256 startBlockHash = Hash256.from(startBlockHex);
        final Hash256 endBlockHash = getHash256FromHex(endBlockHex);

        final List<StorageChangeSet> changesPerBlock = new ArrayList<>();
        final Map<String, String> previousValues = new HashMap<>();
        for (Hash256 blockHash : blockState.range(startBlockHash, endBlockHash)) {
            final Map<String, String> changes = new HashMap<>();
            for (String keyHex : keysHex) {

                byte[] blockStateRoot = blockState.getBlockStateRoot(blockHash).getBytes();
                final Optional<String> currentValueOpt = trieStorage.getByKeyFromMerkle(blockStateRoot, Nibbles.fromHexString(keyHex))
                        .map(NodeData::getValue)
                        .map(StringUtils::toHexWithPrefix);

                final String currentValue = currentValueOpt.orElse(null);
                final String previousValue = previousValues.get(keyHex);

                if (!Objects.equals(currentValue, previousValue)) {
                    changes.put(keyHex, currentValue);
                    previousValues.put(keyHex, currentValue);
                }
            }
            if (!changes.isEmpty()) {
                changesPerBlock.add(new StorageChangeSet(blockHash.toString(), changes));
            }
        }

        return changesPerBlock;
    }

    /**
     * Generates a read proof for a set of keys, scoped to a specific block.
     *
     * @param keyHexList   A list of keys in hexadecimal format for which the read proof is generated.
     * @param blockHashHex The block hash in hexadecimal format to scope the query.
     * @return A map containing the block hash and the read proof for the specified keys within that block.
     */
    public Map<String, Object> stateGetReadProof(final List<String> keyHexList, final String blockHashHex) {
        final Hash256 blockHash = getHash256FromHex(blockHashHex);

        final byte[] blockStateRoot = blockState.getBlockStateRoot(blockHash).getBytes();

        List<String> readProof = keyHexList
                .stream()
                .map(StringUtils::hexToBytes)
                // TODO change implementation logic as this is not correct
                .map(key -> trieStorage.getByKeyFromMerkle(blockStateRoot, Nibbles.fromBytes(key))
                        .orElse(null))
                .filter(Objects::nonNull)
                .map(NodeData::getMerkleValue)
                .map(StringUtils::toHexWithPrefix)
                .toList();

        return Map.of(
                "at", StringUtils.toHexWithPrefix(blockHash.getBytes()),
                "proof", readProof
        );
    }

    private Hash256 getHash256FromHex(String blockHashHex) {
        return blockHashHex != null ? Hash256.from(blockHashHex) : blockState.getHighestFinalizedHash();
    }

}