package com.limechain.runtime;

import com.limechain.config.HostConfig;
import com.limechain.network.Network;
import com.limechain.network.protocol.blockannounce.NodeRole;
import com.limechain.runtime.hostapi.dto.OffchainNetworkState;
import com.limechain.storage.KVRepository;
import com.limechain.storage.crypto.KeyStore;
import com.limechain.storage.offchain.OffchainStorages;
import com.limechain.storage.offchain.OffchainStore;
import com.limechain.storage.offchain.StorageKind;
import com.limechain.trie.DiskTrieAccessor;
import com.limechain.trie.TrieAccessor;
import com.limechain.trie.structure.nibble.Nibbles;
import io.libp2p.core.Host;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Serves as a convenience Spring bean to package all Spring beans necessary
 * for common use-cases regarding building runtime instances.
 *
 * @implNote Composes over {@link RuntimeFactory}, which is the lower-level granular API for runtime instantiation.
 */
@Component
@RequiredArgsConstructor
public class RuntimeBuilder {
    private final KVRepository<String, Object> db;
    private final KeyStore keyStore;
    private final Network network;
    private final HostConfig hostConfig;

    /**
     * Builds a ready-to-execute `Runtime` with dependencies from the global Spring context.
     *
     * @param code the runtime wasm bytecode
     * @return a ready to execute `Runtime` instance
     * @implNote The resulting `Runtime` instance doesn't have access to the trie storage,
     * so it will throw an exception on any attempts to mutate the block's trie state.
     */
    public Runtime buildRuntime(byte[] code) {
        return buildRuntime(code, null);
    }

    /**
     * Creates a copy from a give runtime instance.
     *
     * @param original the instance that we need to copy.
     * @return a copy runtime of the original.
     */
    public Runtime copyRuntime(Runtime original) {
        TrieAccessor trieAccessor = ((RuntimeImpl) original).context.getTrieAccessor();
        return trieAccessor.findStorageValue(Nibbles.fromBytes(":code".getBytes()))
            .map(wasm -> buildRuntime(wasm, new DiskTrieAccessor(trieAccessor)))
            .orElseThrow(() -> new RuntimeException("Runtime code not found in the trie"));
    }

    /**
     * Builds a ready-to-execute `Runtime` with dependencies from the global Spring context.
     *
     * @param code         the runtime wasm bytecode
     * @param trieAccessor provides access to the trie storage for a given block
     * @return a ready to execute `Runtime` instance
     */
    public Runtime buildRuntime(byte[] code, @Nullable TrieAccessor trieAccessor) {
        var localStorage = new OffchainStore(db, StorageKind.LOCAL);
        var persistentStorage = new OffchainStore(db, StorageKind.PERSISTENT);
        // TODO:
        //  This shouldn't be like that: base storage (for offchain_index api) is not the same as persistent storage.
        //  Figure out the right approach, when it becomes relevant.
        var baseStorage = persistentStorage;
        var offchainStorages = new OffchainStorages(localStorage, persistentStorage, baseStorage);

        Host host = network.getHost();
        var offchainNetworkState = new OffchainNetworkState(host.getPeerId(), host.listenAddresses());

        var nodeRole = hostConfig.getNodeRole();
        boolean isValidator = nodeRole == NodeRole.AUTHORING;

        RuntimeFactory.Config cfg = new RuntimeFactory.Config(
            trieAccessor,
            keyStore,
            offchainStorages,
            offchainNetworkState,
            isValidator
        );

        return RuntimeFactory.buildRuntime(code, cfg);
    }
}
