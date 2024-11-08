package com.limechain.runtime;

import com.limechain.network.protocol.blockannounce.scale.BlockHeaderScaleWriter;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.scale.writer.BlockBodyWriter;
import com.limechain.babe.api.BabeApiConfiguration;
import com.limechain.babe.api.scale.BabeApiConfigurationReader;
import com.limechain.runtime.hostapi.dto.RuntimePointerSize;
import com.limechain.runtime.version.RuntimeVersion;
import com.limechain.runtime.version.scale.RuntimeVersionReader;
import com.limechain.sync.fullsync.inherents.InherentData;
import com.limechain.sync.fullsync.inherents.scale.InherentDataWriter;
import com.limechain.trie.structure.nibble.Nibbles;
import com.limechain.utils.LittleEndianUtils;
import com.limechain.utils.scale.ScaleUtils;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wasmer.Instance;
import org.wasmer.Module;

import java.math.BigInteger;
import java.util.Optional;
import java.util.logging.Level;

@Log
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Runtime {
    Module module;
    Context context;
    Instance instance;

    /**
     * Calls an exported runtime function with no parameters.
     * @param function the name Runtime function to call
     * @return the SCALE encoded response
     */
    @Nullable
    public byte[] call(RuntimeEndpoint function) {
        return callInner(function, new RuntimePointerSize(0, 0));
    }

    /**
     * Calls an exported runtime function with parameters.
     * @param function the name Runtime function to call
     * @param parameter the SCALE encoded tuple of parameters
     * @return the SCALE encoded response
     */
    @Nullable
    public byte[] call(RuntimeEndpoint function, @NotNull byte[] parameter) {
        return callInner(function, context.getSharedMemory().writeData(parameter));
    }

    @Nullable
    private byte[] callInner(RuntimeEndpoint function, RuntimePointerSize parameterPtrSize) {
        String functionName = function.getName();
        log.log(Level.FINE, "Making a runtime call: " + functionName);
        Object[] response = instance.exports.getFunction(functionName)
            .apply(parameterPtrSize.pointer(), parameterPtrSize.size());

        if (response == null) {
            return null;
        }

        RuntimePointerSize responsePtrSize = new RuntimePointerSize((long) response[0]);
        return context.getSharedMemory().readData(responsePtrSize);
    }

    private Optional<byte[]> findStorageValue(Nibbles key) {
        return this.context.trieAccessor.findStorageValue(key);
    }

    public BigInteger getGenesisSlotNumber() {
        var optGenesisSlotBytes = this.findStorageValue(RuntimeStorageKey.GENESIS_SLOT.getNibbles());
        return optGenesisSlotBytes.map(LittleEndianUtils::fromLittleEndianByteArray).orElse(null);
    }

    /**
     * @return the {@link BabeApiConfiguration}.
     */
    public BabeApiConfiguration callBabeApiConfiguration() {
        return ScaleUtils.Decode.decode(this.call(RuntimeEndpoint.BABE_API_CONFIGURATION), new BabeApiConfigurationReader());
    }

    /**
     * @return the {@link RuntimeVersion} of this runtime instance.
     * @implNote The runtime version is cached in the context, so no actual runtime invocation is performed.
     */
    public RuntimeVersion getVersion() {
        return this.context.getRuntimeVersion();
    }

    RuntimeVersion callCoreVersion() {
        return ScaleUtils.Decode.decode(this.call(RuntimeEndpoint.CORE_VERSION), new RuntimeVersionReader());
    }

    /**
     * Executes the block by calling `Core_execute_block`.
     * @param block the block to execute
     * @return the SCALE encoded result of the runtime call
     */
    public byte[] executeBlock(Block block) {
        byte[] param = serializeExecuteBlockParameter(block);
        return this.call(RuntimeEndpoint.CORE_EXECUTE_BLOCK, param);
    }

    private static byte[] serializeExecuteBlockParameter(Block block) {
        byte[] encodedUnsealedHeader = ScaleUtils.Encode.encode(
            BlockHeaderScaleWriter.getInstance()::writeUnsealed,
            block.getHeader()
        );
        byte[] encodedBody = ScaleUtils.Encode.encode(BlockBodyWriter.getInstance(), block.getBody());

        return ArrayUtils.addAll(encodedUnsealedHeader, encodedBody);
    }

    /**
     * Checks whether the provided inherents are valid for the block by calling `BlockBuilder_Check_inherents
     * @param block the block to check against
     * @param inherentData inherents to check for validity
     * @return the SCALE encoded result of the runtime call
     */
    public byte[] checkInherents(Block block, InherentData inherentData) {
        byte[] param = serializeCheckInherentsParameter(block, inherentData);
        return this.call(RuntimeEndpoint.BLOCKBUILDER_CHECK_INHERENTS, param);
    }

    private byte[] serializeCheckInherentsParameter(Block block, InherentData inherentData) {
        // The first param is executeBlockParameter
        byte[] executeBlockParameter = serializeExecuteBlockParameter(block);

        // The second param of `BlockBuilder_check_inherents` is the SCALE-encoded InherentData:
        // a SCALE-encoded list of tuples containing an "inherent identifier" (`[u8; 8]`) and a value (`Vec<u8>`).
        byte[] scaleEncodedInherentData = ScaleUtils.Encode.encode(new InherentDataWriter(), inherentData);

        // We simply concat them both
        return ArrayUtils.addAll(executeBlockParameter, scaleEncodedInherentData);
    }

    /**
     * Closes the underlying instance.
     */
    public void close() {
        this.module.close();
        this.instance.close();
    }
}
