package com.limechain.rpc.methods;

import com.googlecode.jsonrpc4j.JsonRpcMethod;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import com.limechain.chain.spec.ChainSpec;
import com.limechain.chain.spec.ChainType;
import com.limechain.chain.spec.PropertyValue;
import com.limechain.exception.rpc.InvalidParametersException;
import com.limechain.rpc.methods.author.AuthorRPC;
import com.limechain.rpc.methods.author.AuthorRPCImpl;
import com.limechain.rpc.methods.chain.ChainRPC;
import com.limechain.rpc.methods.chain.ChainRPCImpl;
import com.limechain.rpc.methods.childstate.ChildStateRPCImpl;
import com.limechain.rpc.methods.offchain.OffchainRPC;
import com.limechain.rpc.methods.offchain.OffchainRPCImpl;
import com.limechain.rpc.methods.state.StateRPC;
import com.limechain.rpc.methods.state.StateRPCImpl;
import com.limechain.rpc.methods.state.dto.StorageChangeSet;
import com.limechain.rpc.methods.sync.SyncRPC;
import com.limechain.rpc.methods.sync.SyncRPCImpl;
import com.limechain.rpc.methods.system.SystemRPC;
import com.limechain.rpc.methods.system.SystemRPCImpl;
import com.limechain.storage.offchain.StorageKind;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Implementation class for each rpc method. This class will become very large in the future
 * when we support more rpc methods, however due to the limitations of jsonrpc4j described in {@link RPCMethods},
 * there's nothing we can do about splitting up the class into several ones for now
 */
@Service
@AutoJsonRpcServiceImpl
@AllArgsConstructor
public class RPCMethodsImpl implements RPCMethods {

    /**
     * References to system rpc method implementation classes
     */
    private final SystemRPCImpl systemRPC;

    /**
     * References to sync rpc method implementation classes
     */
    private final SyncRPCImpl syncRPC;

    /**
     * References to chain rpc method implementation classes
     */
    private final ChainRPCImpl chainRPC;

    /**
     * References to offchain rpc method implementation classes
     */
    private final OffchainRPCImpl offchainRPC;

    /**
     * References to state rpc method implementation classes
     */
    private final StateRPCImpl stateRPC;

    /**
     * References to child state rpc method implementation classes
     */
    private final ChildStateRPCImpl childStateRPC;

    /**
     * References to author rpc method implementation classes
     */
    private final AuthorRPCImpl authorRPC;

    @Override
    public String[] rpcMethods() {
        ArrayList<Method> methods = new ArrayList<>();

        Collections.addAll(methods, RPCMethods.class.getDeclaredMethods());
        Collections.addAll(methods, SystemRPC.class.getDeclaredMethods());
        Collections.addAll(methods, SyncRPC.class.getDeclaredMethods());
        Collections.addAll(methods, ChainRPC.class.getDeclaredMethods());
        Collections.addAll(methods, OffchainRPC.class.getDeclaredMethods());
        Collections.addAll(methods, StateRPC.class.getDeclaredMethods());
        Collections.addAll(methods, AuthorRPC.class.getDeclaredMethods());

        return methods.stream().map(m -> m.getAnnotation(JsonRpcMethod.class).value()).toArray(String[]::new);
    }

    //region SystemRPC methods
    @Override
    public String systemName() {
        return systemRPC.systemName();
    }

    @Override
    public String systemVersion() {
        return systemRPC.systemVersion();
    }

    @Override
    public String systemChain() {
        return systemRPC.systemChain();
    }

    @Override
    public ChainType systemChainType() {
        return systemRPC.systemChainType();
    }

    @Override
    public Map<String, PropertyValue> systemProperties() {
        return systemRPC.systemProperties();
    }

    @Override
    public String[] systemNodeRoles() {
        return systemRPC.systemNodeRoles();
    }

    @Override
    public Map<String, Object> systemHealth() {
        return systemRPC.systemHealth();
    }

    @Override
    public String systemLocalPeerId() {
        return systemRPC.systemLocalPeerId();
    }

    @Override
    public String[] systemLocalListenAddress() {
        return systemRPC.systemLocalListenAddress();
    }

    @Override
    public List<Map<String, Object>> systemSystemPeers() {
        return systemRPC.systemSystemPeers();
    }

    @Override
    public void systemAddReservedPeer(String peerId) {
        systemRPC.systemAddReservedPeer(peerId);
    }

    @Override
    public void systemRemoveReservedPeer(String peerId) {
        systemRPC.systemRemoveReservedPeer(peerId);
    }

    @Override
    public Map<String, Object> systemSyncState() {
        return systemRPC.systemSyncState();
    }

    @Override
    public String systemAccountNextIndex(String accountAddress) {
        return systemRPC.systemAccountNextIndex(accountAddress);
    }

    @Override
    public String systemDryRun(String extrinsic, String blockHash) {
        return systemRPC.systemDryRun(extrinsic, blockHash);
    }
    //endregion

    //region SyncRPC methods
    @Override
    public ChainSpec syncStateGenSyncSpec(boolean raw) {
        return syncRPC.syncStateGetSyncSpec(raw);
    }
    //endregion

    //region ChainRPC methods
    @Override
    public Map<String, Object> chainGetHeader(final String blockHashArg) {
        return this.chainRPC.chainGetHeader(blockHashArg);
    }

    @Override
    public Map<String, Object> chainGetBlock(final String blockHash) {
        return this.chainRPC.chainGetBlock(blockHash);
    }

    @Override
    public Object chainGetBlockHash(final Object... blockNumbers) {
        return this.chainRPC.chainGetBlockHash(blockNumbers);
    }

    @Override
    public Object chainGetHead(final Object... blockNumbers) {
        return chainGetBlockHash(blockNumbers);
    }

    @Override
    public String chainGetFinalizedHead() {
        return this.chainRPC.chainGetFinalizedHead();
    }

    @Override
    public String chainGetFinalisedHead() {
        return chainGetFinalizedHead();
    }
    //endregion

    //region OffchainRPC methods
    @Override
    public void offchainLocalStorageSet(String storageKindStr, String key, String value) {
        final StorageKind storageKind;
        try {
            storageKind = StorageKind.valueOf(storageKindStr);
        } catch (IllegalArgumentException e) {
            throw new InvalidParametersException("Invalid storage kind: " + storageKindStr);
        }
        offchainRPC.offchainLocalStorageSet(storageKind, key, value);
    }

    @Override
    public String offchainLocalStorageGet(String storageKindStr, String key) {
        final StorageKind storageKind;
        try {
            storageKind = StorageKind.valueOf(storageKindStr);
        } catch (IllegalArgumentException e) {
            throw new InvalidParametersException("Invalid storage kind: " + storageKindStr);
        }
        return offchainRPC.offchainLocalStorageGet(storageKind, key);
    }
    //endregion

    //region StateRPC methods
    @Override
    public void stateCall(final String method, final String data, final String blockHash) {
        stateRPC.stateCall(method, data, blockHash);
    }

    @Override
    public String[][] stateGetPairs(final String prefix, final String blockHash) {
        return stateRPC.stateGetPairs(prefix, blockHash);
    }

    @Override
    public List<String> stateGetKeysPaged(final String prefix, final int limit, final String key, final String blockHash) {
        return stateRPC.stateGetKeysPaged(prefix, limit, key, blockHash);
    }

    @Override
    public String stateGetStorage(final String key, final String blockHash) {
        return stateRPC.stateGetStorage(key, blockHash);
    }

    @Override
    public String stateGetStorageHash(final String key, final String blockHash) {
        return stateRPC.stateGetStorageHash(key, blockHash);
    }

    @Override
    public String stateGetStorageSize(final String key, final String blockHash) {
        return stateRPC.stateGetStorageSize(key, blockHash);
    }

    @Override
    public String stateGetStorageSizeAt(final String key, final String blockHash) {
        return stateRPC.stateGetStorageSize(key, blockHash);
    }

    @Override
    public String stateGetMetadata(final String blockHash) {
        return stateRPC.stateGetMetadata(blockHash);
    }

    @Override
    public String stateGetRuntimeVersion(final String blockHash) {
        return stateRPC.stateGetRuntimeVersion(blockHash);
    }

    @Override
    public List<StorageChangeSet> stateQueryStorage(final List<String> key, final String startBlockHash, final String endBlockHash) {
        return stateRPC.stateQueryStorage(key, startBlockHash, endBlockHash);
    }

    @Override
    public List<StorageChangeSet> stateQueryStorageAt(final List<String> key, final String startBlockHash) {
        return stateRPC.stateQueryStorage(key, startBlockHash, startBlockHash);
    }

    @Override
    public Map<String, Object> stateGetReadProof(final List<String> key, final String blockHash) {
        return stateRPC.stateGetReadProof(key, blockHash);
    }
    //endregion

    //region ChildStateRPC methods
    @Override
    public List<String> childStateGetKeys(String childKeyHex, String prefix, String blockHashHex) {
        return childStateRPC.childStateGetKeys(prefix, childKeyHex, blockHashHex);
    }

    @Override
    public String childStateGetStorage(String childKeyHex, String keyHex, String blockHashHex) {
        return childStateRPC.stateGetStorage(childKeyHex, keyHex, blockHashHex);
    }

    @Override
    public String childStateGetStorageHash(String childKeyHex, String keyHex, String blockHashHex) {
        return childStateRPC.stateGetStorageHash(childKeyHex, keyHex, blockHashHex);
    }

    @Override
    public String childStateGetStorageSize(String childKeyHex, String keyHex, String blockHashHex) {
        return childStateRPC.stateGetStorageSize(childKeyHex, keyHex, blockHashHex);
    }
    //endregion

    //region AuthorRPC
    @Override
    public String authorRotateKeys() {
        return authorRPC.authorRotateKeys();
    }

    @Override
    public String authorInsertKey(String keyType, String suri, String publicKey) {
        return authorRPC.authorInsertKey(keyType, suri, publicKey);
    }

    @Override
    public Boolean authorHasKey(String publicKey, String keyType) {
        return authorRPC.authorHasKey(publicKey, keyType);
    }

    @Override
    public Boolean authorHasSessionKeys(String sessionKeys) {
        return authorRPC.authorHasSessionKeys(sessionKeys);
    }

    @Override
    public String authorSubmitExtrinsic(String extrinsic) {
        return authorRPC.authorSubmitExtrinsic(extrinsic);
    }

    @Override
    public String authorSubmitAndWatchExtrinsic(String extrinsic) {
        return authorRPC.authorSubmitAndWatchExtrinsic(extrinsic);
    }
    //endregion
}
