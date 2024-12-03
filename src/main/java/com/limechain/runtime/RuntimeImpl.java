package com.limechain.runtime;

import com.limechain.babe.api.BabeApiConfiguration;
import com.limechain.babe.api.scale.BabeApiConfigurationReader;
import com.limechain.network.protocol.blockannounce.scale.BlockHeaderScaleWriter;
import com.limechain.network.protocol.transaction.scale.TransactionReader;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.scale.reader.BlockHeaderReader;
import com.limechain.network.protocol.warp.scale.writer.BlockBodyWriter;
import com.limechain.rpc.methods.author.dto.DecodedKey;
import com.limechain.rpc.methods.author.dto.DecodedKeysReader;
import com.limechain.runtime.hostapi.dto.RuntimePointerSize;
import com.limechain.runtime.version.RuntimeVersion;
import com.limechain.runtime.version.scale.RuntimeVersionReader;
import com.limechain.sync.fullsync.inherents.InherentData;
import com.limechain.sync.fullsync.inherents.scale.InherentDataWriter;
import com.limechain.transaction.dto.ApplyExtrinsicResult;
import com.limechain.transaction.dto.Extrinsic;
import com.limechain.transaction.dto.ExtrinsicArray;
import com.limechain.transaction.dto.TransactionValidationRequest;
import com.limechain.transaction.dto.TransactionValidationResponse;
import com.limechain.trie.structure.nibble.Nibbles;
import com.limechain.utils.ByteArrayUtils;
import com.limechain.utils.LittleEndianUtils;
import com.limechain.utils.StringUtils;
import com.limechain.utils.scale.ScaleUtils;
import com.limechain.utils.scale.readers.ApplyExtrinsicResultReader;
import com.limechain.utils.scale.readers.TransactionValidationReader;
import com.limechain.utils.scale.writers.BlockInherentsWriter;
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

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
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
        byte[] encodedResponse = callAndBackup(RuntimeEndpoint.TRANSACTION_QUEUE_VALIDATE_TRANSACTION, encodedRequest);

        return ScaleUtils.Decode.decode(encodedResponse, new TransactionValidationReader());
    }

    @Override
    public BlockHeader finalizeBlock() {
        byte[] encodedResponse = call(RuntimeEndpoint.BLOCKBUILDER_FINALIZE_BLOCK);
        return ScaleUtils.Decode.decode(encodedResponse, new BlockHeaderReader());
    }


    @Override
    public byte[] checkInherents(Block block, InherentData inherentData) {
        byte[] encodedRequest = serializeCheckInherentsParameter(block, inherentData);
        return call(RuntimeEndpoint.BLOCKBUILDER_CHECK_INHERENTS, encodedRequest);
    }

    @Override
    public ApplyExtrinsicResult applyExtrinsic(Extrinsic extrinsic) {
        byte[] encodedRequest = ScaleUtils.Encode.encodeAsListOfBytes(ByteArrayUtils.toIterable(extrinsic.getData()));
        byte[] encodedResponse = call(RuntimeEndpoint.BLOCKBUILDER_APPLY_EXTRINISIC, encodedRequest);

        return ScaleUtils.Decode.decode(encodedResponse, new ApplyExtrinsicResultReader());
    }

    @Override
    public ExtrinsicArray inherentExtrinsics(com.limechain.babe.dto.InherentData inherentData) {
        byte[] encodedRequest = ScaleUtils.Encode.encode(new BlockInherentsWriter(), inherentData);
        byte[] encodedResponse = call(RuntimeEndpoint.BLOCKBUILDER_INHERENT_EXTRINISICS, encodedRequest);

        return ScaleUtils.Decode.decode(encodedResponse, new TransactionReader());
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
    public void initializeBlock(BlockHeader blockHeader) {
        byte[] encHeader = ScaleUtils.Encode.encode(BlockHeaderScaleWriter.getInstance(), blockHeader);
        call(RuntimeEndpoint.CORE_INITIALIZE_BLOCK, encHeader);
    }

    @Override
    public BigInteger getGenesisSlotNumber() {
        var optGenesisSlotBytes = this.findStorageValue(RuntimeStorageKey.GENESIS_SLOT.getNibbles());
        return optGenesisSlotBytes.map(LittleEndianUtils::fromLittleEndianByteArray).orElse(null);
    }

    @Override
    public void persistsChanges() {
        context.getTrieAccessor().persistChanges();
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
    private synchronized byte[] call(RuntimeEndpoint function) {
        return callInner(function, new RuntimePointerSize(0, 0));
    }

    /**
     * Calls an exported runtime function with no parameters and backup state changes.
     *
     * @param function the name Runtime function to call
     * @return the SCALE encoded response
     */
    @Nullable
    private synchronized byte[] callAndBackup(RuntimeEndpoint function) {
        context.trieAccessor.prepareBackup();
        byte[] result = callInner(function, new RuntimePointerSize(0, 0));
        context.trieAccessor.backup();

        return result;
    }

    /**
     * Calls an exported runtime function with parameters.
     *
     * @param function  the name Runtime function to call
     * @param parameter the SCALE encoded tuple of parameters
     * @return the SCALE encoded response
     */
    @Nullable
    private synchronized byte[] call(RuntimeEndpoint function, @NotNull byte[] parameter) {
        return callInner(function, context.getSharedMemory().writeData(parameter));
    }

    /**
     * Calls an exported runtime function with parameters and backup state changes.
     *
     * @param function  the name Runtime function to call
     * @param parameter the SCALE encoded tuple of parameters
     * @return the SCALE encoded response
     */
    @Nullable
    private synchronized byte[] callAndBackup(RuntimeEndpoint function, @NotNull byte[] parameter) {
        context.trieAccessor.prepareBackup();
        byte[] result = callInner(function, context.getSharedMemory().writeData(parameter));
        context.trieAccessor.backup();

        return result;
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
}

