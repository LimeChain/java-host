package com.limechain.runtime;

import com.github.luben.zstd.Zstd;
import com.limechain.runtime.allocator.FreeingBumpHeapAllocator;
import com.limechain.runtime.hostapi.DefaultHostApi;
import com.limechain.runtime.hostapi.HostApi;
import com.limechain.runtime.hostapi.WasmExports;
import com.limechain.runtime.hostapi.dto.OffchainNetworkState;
import com.limechain.runtime.memory.WasmMemory;
import com.limechain.runtime.version.RuntimeVersion;
import com.limechain.storage.crypto.KeyStore;
import com.limechain.storage.offchain.OffchainStorages;
import com.limechain.trie.TrieAccessor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.Nullable;
import org.wasmer.ImportObject;
import org.wasmer.Imports;
import org.wasmer.Instance;
import org.wasmer.Memory;
import org.wasmer.Module;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Builds a runtime instance with an explicit context configuration.
 */
@Log
public class RuntimeFactory {
    private static final byte[] ZSTD_PREFIX = new byte[]{82, -68, 83, 118, 70, -37, -114, 5};
    private static final int MAX_ZSTD_DECOMPRESSED_SIZE = 50 * 1024 * 1024;

    private static final int DEFAULT_MEMORY_PAGES = 2048;

    /**
     * Builds and returns a ready-to-execute `Runtime` with an explicit context configuration.
     *
     * @param code   the runtime wasm bytecode
     * @param config an explicit configuration of necessary dependencies
     * @return a ready to execute `Runtime` instance
     */
    public static Runtime buildRuntime(byte[] code, Config config) {
        return buildRuntime(code, config, DefaultHostApi::new);
    }

    /**
     * Builds and returns a ready-to-execute `Runtime` with an explicit context configuration
     * and a provided custom Host API implementation.
     *
     * @param code            the runtime wasm bytecode
     * @param config          an explicit configuration of necessary dependencies
     * @param hostApiProvider a provider for a {@link HostApi} implementation, given the current execution {@link Context}.
     * @return a ready to execute `Runtime` instance
     * @apiNote Providing a custom implementation requires you to extend {@link HostApi}
     * with your custom implementation class.
     * The runtime execution {@link Context} is exposed to you for this purpose.
     * It is minimally contractually binding to construct a {@link HostApi},
     * but you can choose to either use it or not.
     */
    public static Runtime buildRuntime(byte[] code, Config config, Function<Context, HostApi> hostApiProvider) {
        byte[] wasmBinary = zstDecompressIfNecessary(code);

        Module module = new Module(wasmBinary);

        SharedMemory sharedMemory = new SharedMemory(null, null);
        Context context = new Context(
                config.trieAccessor,
                config.keyStore(),
                config.offchainStorages(),
                config.offchainNetworkState(),
                config.isValidator(),
                sharedMemory,
                null
        );

        // Construct the host API implementation
        HostApi hostApi = hostApiProvider.apply(context);

        // Build the imports
        ImportObject.MemoryImport memoryImport = buildMemoryImport(wasmBinary);
        List<ImportObject.FuncImport> functionImports = hostApi.getFunctionImports();

        List<ImportObject> imports = new ArrayList<>(functionImports);
        imports.add(memoryImport);

        // Instantiate the wasm module
        Instance instance = module.instantiate(Imports.from(imports, module));

        // Construct our Runtime instance
        Runtime runtime = new RuntimeImpl(module, context, instance);

        // Inject the wasm memory and the allocator into the shared memory
        // NOTE:
        //  This injection is necessarily delayed to circumvent the circular dependency:
        //  instance needs imports -> imports need shared memory -> shared memory needs instance (to fetch the memory from)
        Memory instanceMemory = instance.exports.getMemory(WasmExports.MEMORY.getValue());
        int heapBase = instance.exports.getGlobal(WasmExports.HEAP_BASE.getValue()).getIntValue();
        sharedMemory.setMemory(new WasmMemory(instanceMemory));
        sharedMemory.setAllocator(new FreeingBumpHeapAllocator(heapBase));

        // We cache the runtime version in the context as it's often needed
        cacheRuntimeVersion(wasmBinary, runtime, context);

        return runtime;
    }

    private static byte[] zstDecompressIfNecessary(byte[] code) {
        byte[] wasmBinaryPrefix = Arrays.copyOfRange(code, 0, 8);
        if (Arrays.equals(wasmBinaryPrefix, ZSTD_PREFIX)) {
            return Zstd.decompress(
                    Arrays.copyOfRange(code, ZSTD_PREFIX.length, code.length),
                    MAX_ZSTD_DECOMPRESSED_SIZE
            );
        }

        return code;
    }

    private static ImportObject.MemoryImport buildMemoryImport(byte[] wasmBinary) {
        // Attempt to parse memory needs from the raw binary source
        ImportObject.MemoryImport memoryImport = WasmSectionUtils.parseMemoryFromBinary(wasmBinary);

        // If no information could be found, build the default import
        if (memoryImport == null) {
            memoryImport = new ImportObject.MemoryImport(WasmSectionUtils.ENV_MODULE_NAME, DEFAULT_MEMORY_PAGES, false);
        }

        return memoryImport;
    }

    private static void cacheRuntimeVersion(byte[] wasmBinary, Runtime runtime, Context context) {
        // Attempt parsing the runtime version from custom sections
        RuntimeVersion runtimeVersion = WasmSectionUtils.parseRuntimeVersionFromBinary(wasmBinary);

        // If we couldn't get the data from the wasm custom sections,
        // we must fall back to calling Core_version
        if (runtimeVersion == null) {
            log.log(Level.INFO, "Couldn't fetch runtime version from custom section, calling 'Core_version'.");
            runtimeVersion = runtime.getVersion();
        }

        context.setRuntimeVersion(runtimeVersion);
    }

    /**
     * A configuration holding all necessary dependencies for runtime calls.
     *
     * @param trieAccessor         used by storage related endpoints for accessing the trie storage for a block
     * @param keyStore             used by crypto host functions to access secret keys
     * @param offchainStorages     used by offchain host functions to access the different offchain storages
     * @param offchainNetworkState used by offchain host functions.
     * @param isValidator          used by offchain host functions to determine
     *                             whether the node is a validator (i.e. authoring node) or not
     * @apiNote Explicitly passing `null`s for some of the dependencies is admissible in case you desire a partial
     * runtime execution context (e.g. when you need a really lightweight runtime invocation).
     * The implicit knowledge of whether a dependency will be used for the runtime invocations about to be performed,
     * however, still remains a responsibility of the caller.
     */
    public record Config(
            @Nullable
            TrieAccessor trieAccessor,
            @Nullable
            KeyStore keyStore,
            @Nullable
            OffchainStorages offchainStorages,
            @Nullable
            OffchainNetworkState offchainNetworkState,
            boolean isValidator
    ) {
        public static final Config EMPTY = new Config(null, null, null, null, false);
    }
}
