package com.limechain.runtime.hostapi;

import lombok.experimental.UtilityClass;
import org.wasmer.ImportObject;
import org.wasmer.Type;

import java.util.Arrays;
import java.util.List;

@UtilityClass
public class MiscellaneousHostFunctions {

    public static List<ImportObject> getFunctions() {
        return Arrays.asList(
                HostFunctions.getImportObject("ext_misc_print_num_version_1", argv -> {
                    return HostFunctions.EMPTY_LIST_OF_NUMBER;
                }, List.of(Type.I64)),
                HostFunctions.getImportObject("ext_misc_print_utf8_version_1", argv -> {
                    return HostFunctions.EMPTY_LIST_OF_NUMBER;
                }, List.of(Type.I64)),
                HostFunctions.getImportObject("ext_misc_print_hex_version_1", argv -> {
                    HostApi.extMiscPrintHex((long) argv.get(0));
                    return HostFunctions.EMPTY_LIST_OF_NUMBER;
                }, List.of(Type.I64)),
                HostFunctions.getImportObject("ext_misc_runtime_version_version_1", argv -> {
                    return argv;
                }, List.of(Type.I64), Type.I64),
                HostFunctions.getImportObject("ext_logging_log_version_1", argv -> {
                    HostApi.extLoggingLog((Integer) argv.get(0), (Long) argv.get(1), (Long) argv.get(2));
                    return HostFunctions.EMPTY_LIST_OF_NUMBER;
                }, Arrays.asList(Type.I32, Type.I64, Type.I64)),
                HostFunctions.getImportObject("ext_logging_max_level", argv -> {
                    HostApi.extLoggingLog((Integer) argv.get(0), (Long) argv.get(1), (Long) argv.get(2));
                    return HostFunctions.EMPTY_LIST_OF_NUMBER;
                }, List.of(), Type.I64));
    }

}
