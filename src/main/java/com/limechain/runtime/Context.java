package com.limechain.runtime;

import com.limechain.runtime.hostapi.dto.OffchainNetworkState;
import com.limechain.runtime.version.RuntimeVersion;
import com.limechain.storage.crypto.KeyStore;
import com.limechain.storage.offchain.OffchainStorages;
import com.limechain.trie.TrieAccessor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Holds <strong>all</strong> necessary dependencies for a successful runtime invocation.
 * The main purpose of this class is to be explicit and exhaustive.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Context {
    /**
     * Used by storage related endpoints for accessing the trie storage for a block.
     */
    TrieAccessor trieAccessor;

    /**
     * Used by crypto host functions to access secret keys.
     */
    KeyStore keyStore;

    /**
     * Used by offchain host functions to access the different offchain storages.
     *
     * @see OffchainStorages
     */
    OffchainStorages offchainStorages;

    /**
     * Used by offchain host functions to provide information about the offchain network state.
     */
    OffchainNetworkState offchainNetworkState;

    /**
     * Used by offchain host functions to determine
     * whether the node is a validator (i.e. authoring node) or not.
     */
    boolean isValidator;

    /**
     * The shared memory between the host and the runtime.
     */
    @Setter(AccessLevel.PACKAGE)
    SharedMemory sharedMemory;

    /**
     * The runtime version is here only to be cached as it's often needed.
     */
    @Setter(AccessLevel.PACKAGE)
    RuntimeVersion runtimeVersion;
}
