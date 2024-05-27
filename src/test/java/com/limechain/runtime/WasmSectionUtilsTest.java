package com.limechain.runtime;

import com.limechain.runtime.version.ApiVersion;
import com.limechain.runtime.version.ApiVersions;
import com.limechain.runtime.version.RuntimeVersion;
import com.limechain.runtime.version.StateVersion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WasmSectionUtilsTest {
    @Test
    void test_parseRuntimeVersionFromBinary_success() throws IOException {
        // expected data extracted from Smoldot, given the same wasm blob
        RuntimeVersion expectedRuntimeVersion = new RuntimeVersion();
        expectedRuntimeVersion.setSpecName("node-template");
        expectedRuntimeVersion.setImplementationName("node-template");
        expectedRuntimeVersion.setImplementationVersion(BigInteger.valueOf(1));
        expectedRuntimeVersion.setAuthoringVersion(BigInteger.valueOf(1));
        expectedRuntimeVersion.setSpecVersion(BigInteger.valueOf(100));
        expectedRuntimeVersion.setApis(ApiVersions.of(List.of(
            new ApiVersion(
                new byte[] {(byte) 223, (byte) 106, (byte) 203, (byte) 104, (byte) 153, (byte) 7, (byte) 96, (byte) 155},
                BigInteger.valueOf(4)
            ),
            new ApiVersion(
                new byte[] {(byte) 55, (byte) 227, (byte) 151, (byte) 252, (byte) 124, (byte) 145, (byte) 245, (byte) 228},
                BigInteger.valueOf(2)
            ),
            new ApiVersion(
                new byte[] {(byte) 64, (byte) 254, (byte) 58, (byte) 212, (byte) 1, (byte) 248, (byte) 149, (byte) 154},
                BigInteger.valueOf(6)
            ),
            new ApiVersion(
                new byte[] {(byte) 210, (byte) 188, (byte) 152, (byte) 151, (byte) 238, (byte) 208, (byte) 143, (byte) 21},
                BigInteger.valueOf(3)
            ),
            new ApiVersion(
                new byte[] {(byte) 247, (byte) 139, (byte) 39, (byte) 139, (byte) 229, (byte) 63, (byte) 69, (byte) 76},
                BigInteger.valueOf(2)
            ),
            new ApiVersion(
                new byte[] {(byte) 221, (byte) 113, (byte) 141, (byte) 92, (byte) 197, (byte) 50, (byte) 98, (byte) 212},
                BigInteger.valueOf(1)
            ),
            new ApiVersion(
                new byte[] {(byte) 171, (byte) 60, (byte) 5, (byte) 114, (byte) 41, (byte) 31, (byte) 235, (byte) 139},
                BigInteger.valueOf(1)
            ),
            new ApiVersion(
                new byte[] {(byte) 237, (byte) 153, (byte) 197, (byte) 172, (byte) 178, (byte) 94, (byte) 237, (byte) 245},
                BigInteger.valueOf(3)
            ),
            new ApiVersion(
                new byte[] {(byte) 188, (byte) 157, (byte) 137, (byte) 144, (byte) 79, (byte) 91, (byte) 146, (byte) 63},
                BigInteger.valueOf(1)
            ),
            new ApiVersion(
                new byte[] {(byte) 55, (byte) 200, (byte) 187, (byte) 19, (byte) 80, (byte) 169, (byte) 162, (byte) 168},
                BigInteger.valueOf(4)
            ),
            new ApiVersion(
                new byte[] {(byte) 243, (byte) 255, (byte) 20, (byte) 213, (byte) 171, (byte) 82, (byte) 112, (byte) 89},
                BigInteger.valueOf(3)
            ),
            new ApiVersion(
                new byte[] {(byte) 103, (byte) 244, (byte) 184, (byte) 251, (byte) 168, (byte) 88, (byte) 120, (byte) 42},
                BigInteger.valueOf(1)
            ),
            new ApiVersion(
                new byte[] {(byte) 251, (byte) 197, (byte) 119, (byte) 185, (byte) 215, (byte) 71, (byte) 239, (byte) 214},
                BigInteger.valueOf(1)
            )
        )));
        expectedRuntimeVersion.setTransactionVersion(BigInteger.valueOf(1));
        expectedRuntimeVersion.setStateVersion(StateVersion.V1);

        // we're using a recent node template wasm blob which contains the runtime version in custom sections
        try (InputStream wasmBytesInput = this.getClass().getResourceAsStream("/runtime_version_custom_section.wasm")) {
            byte[] wasmBytes = Objects.requireNonNull(wasmBytesInput).readAllBytes();

            RuntimeVersion actualRuntimeVersion = WasmSectionUtils.parseRuntimeVersionFromBinary(wasmBytes);

            assertEquals(expectedRuntimeVersion, actualRuntimeVersion);
        }
    }
}