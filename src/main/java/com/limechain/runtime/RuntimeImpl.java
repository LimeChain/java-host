package com.limechain.runtime;

import com.limechain.babe.api.BabeApiConfiguration;
import com.limechain.babe.api.scale.BabeApiConfigurationReader;
import com.limechain.network.protocol.blockannounce.scale.BlockHeaderScaleWriter;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.scale.writer.BlockBodyWriter;
import com.limechain.rpc.methods.author.dto.DecodedKey;
import com.limechain.rpc.methods.author.dto.DecodedKeysReader;
import com.limechain.runtime.hostapi.dto.RuntimePointerSize;
import com.limechain.runtime.version.RuntimeVersion;
import com.limechain.runtime.version.scale.RuntimeVersionReader;
import com.limechain.sync.fullsync.inherents.InherentData;
import com.limechain.sync.fullsync.inherents.scale.InherentDataWriter;
import com.limechain.transaction.dto.TransactionValidationRequest;
import com.limechain.transaction.dto.TransactionValidationResponse;
import com.limechain.trie.TrieAccessor;
import com.limechain.utils.StringUtils;
import com.limechain.utils.scale.ScaleUtils;
import com.limechain.utils.scale.readers.TransactionValidationReader;
import com.limechain.utils.scale.writers.TransactionValidationWriter;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wasmer.Instance;
import org.wasmer.Module;

import java.util.List;
import java.util.logging.Level;

@Log
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class RuntimeImpl implements Runtime {

    Module module;
    Context context;
    Instance instance;

    @Override
    public BabeApiConfiguration getBabeApiConfiguration() {
        return ScaleUtils.Decode.decode(call(RuntimeEndpoint.BABE_API_CONFIGURATION), new BabeApiConfigurationReader());
    }

    @Override
    public List<DecodedKey> decodeSessionKeys(String sessionKeys) {
        byte[] encodedRequest = ScaleUtils.Encode.encode(
                ScaleCodecWriter::writeByteArray, StringUtils.hexToBytes(sessionKeys));
        byte[] encodedResponse = call(RuntimeEndpoint.SESSION_KEYS_DECODE_SESSION_KEYS, encodedRequest);

        return ScaleUtils.Decode.decode(encodedResponse, new DecodedKeysReader());
    }

    @Override
    public RuntimeVersion getCachedVersion() {
        return context.getRuntimeVersion();
    }

    @Override
    public RuntimeVersion getVersion() {
        return ScaleUtils.Decode.decode(call(RuntimeEndpoint.CORE_VERSION), new RuntimeVersionReader());
    }

    @Override
    public TransactionValidationResponse validateTransaction(TransactionValidationRequest request) {
        byte[] encodedRequest = ScaleUtils.Encode.encode(new TransactionValidationWriter(), request);
        byte[] encodedResponse = call(RuntimeEndpoint.TRANSACTION_QUEUE_VALIDATE_TRANSACTION, encodedRequest);

        return ScaleUtils.Decode.decode(encodedResponse, new TransactionValidationReader());
    }

    @Override
    public TrieAccessor getTrieAccessor() {
        return context.getTrieAccessor();
    }

    @Override
    public byte[] checkInherents(Block block, InherentData inherentData) {
        byte[] encodedRequest = serializeCheckInherentsParameter(block, inherentData);
        return call(RuntimeEndpoint.BLOCKBUILDER_CHECK_INHERENTS, encodedRequest);
    }

    @Override
    public byte[] generateSessionKeys(byte[] scaleSeed) {
        byte[] encodedRequest = ScaleUtils.Encode.encodeOptional(ScaleCodecWriter::writeByteArray, scaleSeed);
        return call(RuntimeEndpoint.SESSION_KEYS_GENERATE_SESSION_KEYS, encodedRequest);
    }

    @Override
    public byte[] getMetadata() {
        return call(RuntimeEndpoint.METADATA_METADATA);
    }

    @Override
    public void executeBlock(Block block) {
        byte[] param = serializeExecuteBlockParameter(block);
        call(RuntimeEndpoint.CORE_EXECUTE_BLOCK, param);
    }

    @Override
    public void close() {
        module.close();
        instance.close();
    }

    private byte[] serializeExecuteBlockParameter(Block block) {
        byte[] encodedUnsealedHeader = ScaleUtils.Encode.encode(
                BlockHeaderScaleWriter.getInstance()::writeUnsealed,
                block.getHeader()
        );
        byte[] encodedBody = ScaleUtils.Encode.encode(BlockBodyWriter.getInstance(), block.getBody());

        return ArrayUtils.addAll(encodedUnsealedHeader, encodedBody);
    }

    private byte[] serializeCheckInherentsParameter(Block block, InherentData inherentData) {
        byte[] executeBlockParameter = serializeExecuteBlockParameter(block);
        byte[] scaleEncodedInherentData = ScaleUtils.Encode.encode(new InherentDataWriter(), inherentData);
        return ArrayUtils.addAll(executeBlockParameter, scaleEncodedInherentData);
    }

    /**
     * Calls an exported runtime function with no parameters.
     *
     * @param function the name Runtime function to call
     * @return the SCALE encoded response
     */
    @Nullable
    private byte[] call(RuntimeEndpoint function) {
        return callInner(function, new RuntimePointerSize(0, 0));
    }

    /**
     * Calls an exported runtime function with parameters.
     *
     * @param function  the name Runtime function to call
     * @param parameter the SCALE encoded tuple of parameters
     * @return the SCALE encoded response
     */
    @Nullable
    private byte[] call(RuntimeEndpoint function, @NotNull byte[] parameter) {
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
}

