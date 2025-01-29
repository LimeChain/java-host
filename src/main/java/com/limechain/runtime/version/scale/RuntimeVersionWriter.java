package com.limechain.runtime.version.scale;

import com.limechain.runtime.version.ApiVersions;
import com.limechain.runtime.version.RuntimeVersion;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.math.BigInteger;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RuntimeVersionWriter implements ScaleWriter<RuntimeVersion> {

    private static final RuntimeVersionWriter INSTANCE = new RuntimeVersionWriter();

    public static RuntimeVersionWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter scaleCodecWriter, RuntimeVersion runtimeVersion) throws IOException {
        scaleCodecWriter.writeAsList(runtimeVersion.getSpecName().getBytes());
        scaleCodecWriter.writeAsList(runtimeVersion.getImplementationName().getBytes());
        scaleCodecWriter.writeUint32(runtimeVersion.getAuthoringVersion().longValueExact());
        scaleCodecWriter.writeUint32(runtimeVersion.getSpecVersion().longValueExact());
        scaleCodecWriter.writeUint32(runtimeVersion.getImplementationVersion().longValueExact());

        // Write the api versions
        scaleCodecWriter.write(ApiVersions.Scale.WRITER, runtimeVersion.getApis());

        // Write transaction version if it's present (older runtimes don't include that field)
        BigInteger transactionVersion = runtimeVersion.getTransactionVersion();
        if (transactionVersion != null) {
            scaleCodecWriter.writeUint32(transactionVersion.longValueExact());
        }

        // Write the state version if it's present. Older runtimes miss this field, so StateVersion 0 is to be presumed.
        scaleCodecWriter.writeByte(runtimeVersion.getStateVersion().asInt());
    }
}
