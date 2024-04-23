package com.limechain.runtime.hostapi;

import com.limechain.runtime.Runtime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.wasmer.ImportObject;
import org.wasmer.Type;

import java.util.Arrays;
import java.util.List;

/**
 * Implementations of the Allocator HostAPI functions
 * For more info check
 * {<a href="https://spec.polkadot.network/chap-host-api#sect-allocator-api">Allocator API</a>}
 */
@Log
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AllocatorHostFunctions {
    private final Runtime runtime;

    public static List<ImportObject> getFunctions(Runtime runtime) {
        return new AllocatorHostFunctions(runtime).buildFunctions();
    }

    public List<ImportObject> buildFunctions() {
        return Arrays.asList(
                HostApi.getImportObject("ext_allocator_malloc_version_1", argv ->
                                extAllocatorMallocVersion1(argv.get(0).intValue()),
                        List.of(Type.I32), Type.I32),
                HostApi.getImportObject("ext_allocator_free_version_1", argv ->
                                extAllocatorFreeVersion1(argv.get(0).intValue()),
                        List.of(Type.I32)));
    }

    /**
     * Allocates the given number of bytes and returns the pointer to that memory location.
     *
     * @param size the size of the buffer to be allocated.
     * @return a pointer to the allocated buffer.
     */
    public int extAllocatorMallocVersion1(int size) {
        log.finest("extAllocatorMallocVersion1");
        return runtime.allocate(size).pointer();

    }

    /**
     * Free the given pointer.
     *
     * @param pointer a pointer to the memory buffer to be freed.
     */
    public void extAllocatorFreeVersion1(int pointer) {
        log.finest("extAllocatorFreeVersion1");
        runtime.deallocate(pointer);
    }
}
