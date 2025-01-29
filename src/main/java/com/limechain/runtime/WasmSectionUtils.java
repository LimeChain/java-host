package com.limechain.runtime;

import com.dylibso.chicory.log.Logger;
import com.dylibso.chicory.wasm.Module;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.Export;
import com.dylibso.chicory.wasm.types.ExportSection;
import com.dylibso.chicory.wasm.types.Import;
import com.dylibso.chicory.wasm.types.MemoryImport;
import com.dylibso.chicory.wasm.types.MemoryLimits;
import com.dylibso.chicory.wasm.types.MemorySection;
import com.dylibso.chicory.wasm.types.SectionId;
import com.dylibso.chicory.wasm.types.UnknownCustomSection;
import com.limechain.runtime.version.ApiVersions;
import com.limechain.runtime.version.RuntimeVersion;
import com.limechain.runtime.version.scale.RuntimeVersionReader;
import com.limechain.utils.scale.ScaleUtils;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;
import org.wasmer.ImportObject;

import java.io.ByteArrayInputStream;
import java.util.Objects;
import java.util.Optional;

@UtilityClass
public class WasmSectionUtils {
    private static final String RUNTIME_VERSION_SECTION_NAME = "runtime_version";
    private static final String RUNTIME_APIS_SECTION_NAME = "runtime_apis";
    private static final String MEMORY_IMPORT_NAME = "memory";
    static final String ENV_MODULE_NAME = "env";

    /**
     * Parses the runtime version if both wasm custom sections ("runtime_apis" and "runtime_version") are present.
     *
     * @param wasmBinary the wasm blob
     * @return the parsed RuntimeVersion if both custom sections are present, null otherwise
     */
    @Nullable
    public RuntimeVersion parseRuntimeVersionFromBinary(byte[] wasmBinary) {
        Module moduleWithCustomSections = toModuleWithSections(wasmBinary, SectionId.CUSTOM);

        UnknownCustomSection runtimeApis =
                (UnknownCustomSection) moduleWithCustomSections.customSection(RUNTIME_APIS_SECTION_NAME);
        UnknownCustomSection runtimeVersion =
                (UnknownCustomSection) moduleWithCustomSections.customSection(RUNTIME_VERSION_SECTION_NAME);

        // If we're missing any of the two custom sections, fallback to `Core_runtime`
        if (Objects.isNull(runtimeApis) || Objects.isNull(runtimeVersion)) {
            return null;
        }

        // Read as many api versions as there are (we don't know the length for some reason).
        ApiVersions apiVersions = ApiVersions.decodeNoLength(runtimeApis.bytes());

        // Construct the runtime version partially uninitialized,
        // and then immediately set its apis we've read from the custom section.
        RuntimeVersion version = ScaleUtils.Decode.decode(runtimeVersion.bytes(), RuntimeVersionReader.getInstance());
        version.setApis(apiVersions);

        return version;
    }

    /**
     * Parses the import section of a wasm blob and extracts the "memory" import if present.
     *
     * @param wasmBinary the wasm blob
     * @return the parsed {@link ImportObject.MemoryImport} if present, null otherwise.
     */
    @Nullable
    public ImportObject.MemoryImport parseMemoryFromBinary(byte[] wasmBinary) {
        Module moduleWithSections =
                toModuleWithSections(wasmBinary, SectionId.IMPORT, SectionId.MEMORY, SectionId.EXPORT);
        Integer initialPagesLimit = null;
        boolean isShared = false;

        // Per Runtime spec only one memory should be available for each module.
        if (isMemorySectionValid(moduleWithSections.memorySection()) &&
            isExportSectionValid(moduleWithSections.exportSection())) {
            MemoryLimits limits = moduleWithSections.memorySection().getMemory(0).memoryLimits();
            initialPagesLimit = limits.initialPages();
            isShared = limits.shared();
        } else if (moduleWithSections.importSection() != null) {
            Optional<Import> parsedMemoryImport =
                    moduleWithSections.importSection().stream().filter(i -> i.name().equals(MEMORY_IMPORT_NAME))
                            .findFirst();
            if (parsedMemoryImport.isPresent()) {
                MemoryLimits limits = ((MemoryImport) parsedMemoryImport.get()).limits();
                initialPagesLimit = limits.initialPages();
                isShared = limits.shared();
            }
        }

        return initialPagesLimit != null ? new ImportObject.MemoryImport(ENV_MODULE_NAME, initialPagesLimit, isShared) :
                null;
    }

    private boolean isMemorySectionValid(@Nullable MemorySection memorySection) {
        return memorySection != null && memorySection.memoryCount() == 1;
    }

    private boolean isExportSectionValid(@Nullable ExportSection exportSection) {
        if (exportSection == null) {
            return false;
        }

        for (int i = 0; i < exportSection.exportCount(); i++) {
            Export export = exportSection.getExport(i);
            if (export.name().equals(MEMORY_IMPORT_NAME)) {
                return true;
            }
        }

        return false;
    }

    private Module toModuleWithSections(byte[] wasmBinary, int... sectionIds) {
        Parser parser = new Parser(new ChicoryLogger());
        for (int sectionId : sectionIds) {
            parser.includeSection(sectionId);
        }

        return parser.parseModule(new ByteArrayInputStream(wasmBinary));
    }

    static class ChicoryLogger implements Logger {

        private static final System.Logger LOGGER = System.getLogger("com.dylibso.chicory.wasm");

        @Override
        public void log(Level level, String msg, Throwable throwable) {
            if (isLoggable(level)) {
                System.Logger.Level sll = this.toSystemLoggerLevel(level);
                LOGGER.log(sll, msg, throwable);
            }
        }

        public boolean isLoggable(com.dylibso.chicory.log.Logger.Level level) {
            return this.toSystemLoggerLevel(level) != null;
        }

        System.Logger.Level toSystemLoggerLevel(com.dylibso.chicory.log.Logger.Level level) {
            switch (level) {
                case TRACE:
                    return System.Logger.Level.TRACE;
                case WARNING:
                    return System.Logger.Level.WARNING;
                case ERROR:
                    return System.Logger.Level.ERROR;
                default:
                    return null;
            }
        }

    }
}
