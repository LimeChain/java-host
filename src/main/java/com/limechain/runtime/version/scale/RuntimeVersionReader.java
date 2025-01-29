package com.limechain.runtime.version.scale;

import com.limechain.runtime.version.ApiVersions;
import com.limechain.runtime.version.RuntimeVersion;
import com.limechain.runtime.version.StateVersion;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RuntimeVersionReader implements ScaleReader<RuntimeVersion> {

    private static final RuntimeVersionReader INSTANCE = new RuntimeVersionReader();

    public static RuntimeVersionReader getInstance() {
        return INSTANCE;
    }

    @Override
    public RuntimeVersion read(ScaleCodecReader reader) {
        RuntimeVersion runtimeVersion = new RuntimeVersion();
        runtimeVersion.setSpecName(reader.readString());
        runtimeVersion.setImplementationName(reader.readString());
        runtimeVersion.setAuthoringVersion(BigInteger.valueOf(reader.readUint32()));
        runtimeVersion.setSpecVersion(BigInteger.valueOf(reader.readUint32()));
        runtimeVersion.setImplementationVersion(BigInteger.valueOf(reader.readUint32()));

        // Read the api versions
        runtimeVersion.setApis(reader.read(ApiVersions.Scale.READER));

        // Read transaction version if it's present (older runtimes don't include that field)
        BigInteger transactionVersion = reader.hasNext() ? BigInteger.valueOf(reader.readUint32()) : null;
        runtimeVersion.setTransactionVersion(transactionVersion);

        // Read the state version if it's present. Older runtimes miss this field, so StateVersion 0 is to be presumed.
        int stateVersion = reader.hasNext() ? reader.readUByte() : 0;
        runtimeVersion.setStateVersion(StateVersion.fromInt(stateVersion));

        return runtimeVersion;
    }
}
