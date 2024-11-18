package com.limechain.rpc.methods.author;

import com.googlecode.jsonrpc4j.JsonRpcMethod;
import com.limechain.rpc.config.UnsafeRpcMethod;

/**
 * Interface which holds all author rpc methods and their interfaces
 *
 * @JsonRpcMethod Sets the method name used whenever jsonrpc request is received
 */
public interface AuthorRPC {

    @UnsafeRpcMethod
    @JsonRpcMethod("author_rotateKeys")
    String authorRotateKeys();

    @UnsafeRpcMethod
    @JsonRpcMethod("author_insertKey")
    String authorInsertKey(String keyType, String suri, String publicKey);

    @UnsafeRpcMethod
    @JsonRpcMethod("author_hasKey")
    Boolean authorHasKey(String publicKey, String keyType);

    @UnsafeRpcMethod
    @JsonRpcMethod("author_hasSessionKeys")
    Boolean authorHasSessionKeys(String sessionKeys);

    @UnsafeRpcMethod
    @JsonRpcMethod("author_submitExtrinsic")
    String authorSubmitExtrinsic(String extrinsic);
}
