package com.limechain.storage.block.map;

import com.limechain.runtime.Runtime;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@Log
public class HashToRuntime {
    private final LinkedHashMap<Hash256, Runtime> mapping = new LinkedHashMap<>();

    /**
     * Gets the runtime instance for a given block hash
     *
     * @param hash block hash
     * @return runtime instance
     */
    public Runtime get(Hash256 hash) {
        return mapping.get(hash);
    }

    /**
     * Stores a runtime instance for a given block hash
     *
     * @param hash     block hash
     * @param instance runtime instance to store
     */
    public void set(Hash256 hash, Runtime instance) {
        mapping.put(hash, instance);
    }

    /**
     * Deletes a runtime instance for a given block hash
     *
     * @param hash block hash
     */
    public void delete(Hash256 hash) {
        mapping.remove(hash);
    }

    /**
     * Handles pruning and recording on block finalisation
     *
     * @param newCanonicalBlockHashes the block hashes of the blocks newly finalized
     *                                The last element is the finalized block hash
     */
    public void onFinalisation(List<Hash256> newCanonicalBlockHashes) {
        if (mapping.isEmpty()) {
            log.warning("No runtimes in the mapping");
            return;
        }

        final Hash256 finalizedHash = newCanonicalBlockHashes.get(0);

        // If there's only one runtime in the mapping, update its key.
        if (mapping.size() == 1) {
            Runtime uniqueAvailableInstance = mapping.values().iterator().next();
            mapping.clear();
            mapping.put(finalizedHash, uniqueAvailableInstance);
            return;
        }

        Runtime inMemoryRuntime = null;
        for (Hash256 newCanonicalBlockHash : newCanonicalBlockHashes) {
            if (mapping.containsKey(newCanonicalBlockHash)) {
                inMemoryRuntime = mapping.get(newCanonicalBlockHash);
                break;
            }
        }

        if (inMemoryRuntime == null) return;

        //Since we keep order of insertion everything before the finalized runtime is pruned.
        Iterator<Map.Entry<Hash256, Runtime>> iterator = mapping.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Hash256, Runtime> current = iterator.next();
            if (finalizedHash.equals(current.getKey())) {
                break;
            }

            current.getValue().close();
            iterator.remove();
        }
    }
}
