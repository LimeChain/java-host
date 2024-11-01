package com.limechain.runtime;

import com.limechain.babe.api.BabeApiConfiguration;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.rpc.methods.author.dto.DecodedKey;
import com.limechain.runtime.version.RuntimeVersion;
import com.limechain.sync.fullsync.inherents.InherentData;
import com.limechain.transaction.dto.TransactionValidationRequest;
import com.limechain.transaction.dto.TransactionValidationResponse;
import com.limechain.trie.TrieAccessor;

import java.util.List;

public interface Runtime {

    BabeApiConfiguration getBabeApiConfiguration();

    List<DecodedKey> decodeSessionKeys(String sessionKeys);

    RuntimeVersion getCachedVersion();

    RuntimeVersion getVersion();

    TransactionValidationResponse validateTransaction(TransactionValidationRequest request);

    TrieAccessor getTrieAccessor();

    byte[] checkInherents(Block block, InherentData inherentData);

    byte[] generateSessionKeys(byte[] scaleSeed);

    byte[] getMetadata();

    void executeBlock(Block block);

    void close();
}
