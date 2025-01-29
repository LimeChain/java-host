package com.limechain.runtime.hostapi;

import com.limechain.exception.scale.ScaleEncodingException;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.RuntimeFactory;
import com.limechain.runtime.SharedMemory;
import com.limechain.runtime.hostapi.dto.RuntimePointerSize;
import com.limechain.runtime.version.scale.RuntimeVersionWriter;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.apache.tomcat.util.buf.HexUtils;
import org.springframework.lang.Nullable;
import org.wasmer.ImportObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;

import static com.limechain.runtime.hostapi.PartialHostApi.newImportObjectPair;

/**
 * Implementations of the Miscellaneous and Logging HostAPI functions
 * For more info check
 * {<a href="https://spec.polkadot.network/chap-host-api#sect-misc-api">Miscellaneous API</a>}
 * {<a href="https://spec.polkadot.network/chap-host-api#sect-logging-api">Logging API</a>}
 */
@Log
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class MiscellaneousHostFunctions implements PartialHostApi {

    private final SharedMemory sharedMemory;

    @Override
    public Map<Endpoint, ImportObject.FuncImport> getFunctionImports() {
        return Map.ofEntries(
                newImportObjectPair(Endpoint.ext_misc_print_num_version_1, argv -> {
                    printNumV1(argv.get(0));
                }),
                newImportObjectPair(Endpoint.ext_misc_print_utf8_version_1, argv -> {
                    printUtf8V1(new RuntimePointerSize(argv.get(0)));
                }),
                newImportObjectPair(Endpoint.ext_misc_print_hex_version_1, argv -> {
                    printHexV1(new RuntimePointerSize(argv.get(0)));
                }),
                newImportObjectPair(Endpoint.ext_misc_runtime_version_version_1, argv -> {
                    return runtimeVersionV1(new RuntimePointerSize(argv.get(0))).pointerSize();
                }),
                newImportObjectPair(Endpoint.ext_logging_log_version_1, argv -> {
                    logV1(argv.get(0).intValue(), new RuntimePointerSize(argv.get(1)), new RuntimePointerSize(argv.get(2)));
                }),
                newImportObjectPair(Endpoint.ext_logging_max_level_version_1, argv -> {
                    return maxLevelV1();
                })
        );
    }

    /**
     * Print a number.
     *
     * @param number the number to be printed
     */
    public void printNumV1(Number number) {
        log.fine("Printing number from runtime: " + number);
    }

    /**
     * Print a valid UTF8 encoded buffer.
     *
     * @param strPointer a pointer-size to the valid buffer to be printed.
     */
    public void printUtf8V1(RuntimePointerSize strPointer) {
        byte[] data = sharedMemory.readData(strPointer);

        final String strToPrint = new String(data, StandardCharsets.UTF_8);
        log.fine("Printing utf8 from runtime: " + strToPrint);
    }

    /**
     * Print any buffer in hexadecimal representation.
     *
     * @param pointer a pointer-size to the buffer to be printed.
     */
    public void printHexV1(RuntimePointerSize pointer) {
        byte[] data = sharedMemory.readData(pointer);

        final String hexString = HexUtils.toHexString(data);
        log.fine("Printing hex from runtime: " + hexString);
    }

    /**
     * Extract the Runtime version of the given Wasm blob by calling Core_version. Returns the SCALE encoded runtime
     * version or None if the call fails. This function gets primarily used when upgrading Runtimes.
     * <p>
     * <b>Caution</b>
     * Calling this function is very expensive and should only be done very occasionally. For getting the runtime
     * version, it requires instantiating the Wasm blob and calling the Core_version function in this blob.
     *
     * @param data a pointer-size to the Wasm blob
     * @return a pointer-size to the SCALE encoded Option value containing the Runtime version of the given Wasm blob
     * which is encoded as a byte array.
     */
    public RuntimePointerSize runtimeVersionV1(RuntimePointerSize data) {
        byte[] wasmBlob = sharedMemory.readData(data);

        byte[] versionOption;

        try {
            Runtime runtime = RuntimeFactory.buildRuntime(wasmBlob, RuntimeFactory.Config.EMPTY);
            byte[] runtimeVersionData = ScaleUtils.Encode.encode(
                    RuntimeVersionWriter.getInstance(), runtime.getCachedVersion());

            versionOption = scaleEncodedOption(runtimeVersionData);
        } catch (UnsatisfiedLinkError e) {
            log.log(Level.SEVERE, "Error loading wasm module: " + e.getMessage());
            versionOption = scaleEncodedOption(null);
        }

        return sharedMemory.writeData(versionOption);
    }

    /**
     * Request to print a log message on the host. Note that this will be only displayed if the host is enabled to
     * display log messages with given level and target.
     *
     * @param level      the log level
     * @param targetPtr  a pointer-size to the string which contains the path, module or location from where the log
     *                   was executed.
     * @param messagePtr a pointer-size to the UTF-8 encoded log message.
     */
    public void logV1(int level, RuntimePointerSize targetPtr, RuntimePointerSize messagePtr) {
        byte[] target = sharedMemory.readData(targetPtr);
        byte[] message = sharedMemory.readData(messagePtr);

        final String messageToPrint = new String(message, StandardCharsets.UTF_8);
        final String targetToPrint = new String(target);

        log.log(internalGetLogLevel(level), String.format("Log message from runtime: target=%s, message=%s",
                targetToPrint,
                messageToPrint));
    }

    private Level internalGetLogLevel(int i) {
        return switch (i) {
            case 0 -> Level.SEVERE;
            case 1 -> Level.WARNING;
            case 2 -> Level.INFO;
            case 3 -> Level.FINE;
            default -> Level.FINEST;
        };
    }

    /**
     * Returns the max logging level used by the host.
     * <p>
     * Levels are as follows:
     * Error = 1
     * Warn = 2
     * Info = 3
     * Debug = 4
     * Trace = 5
     * </p>
     * We have Severe, Warning, Info, Fine and Finest
     * </p>
     * If we map
     * Severe -> Error
     * Warning -> Warn
     * Info -> Info
     * Fine -> Debug
     * Finest -> Trace
     * </p>
     * our max level is 5. (The returned value is the index of the level - 4)
     *
     * @return the max log level used by the host.
     */
    public int maxLevelV1() {
        return 4;
    }

    private byte[] scaleEncodedOption(@Nullable byte[] data) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ScaleCodecWriter writer = new ScaleCodecWriter(buf)) {
            writer.writeOptional(ScaleCodecWriter::writeByteArray, data);
        } catch (IOException e) {
            throw new ScaleEncodingException(e);
        }
        return buf.toByteArray();
    }
}
