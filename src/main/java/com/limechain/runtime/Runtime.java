package com.limechain.runtime;

import com.limechain.babe.api.BabeApiConfiguration;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.rpc.methods.author.dto.DecodedKey;
import com.limechain.runtime.version.RuntimeVersion;
import com.limechain.sync.fullsync.inherents.InherentData;
import com.limechain.transaction.dto.TransactionValidationRequest;
import com.limechain.transaction.dto.TransactionValidationResponse;

import java.math.BigInteger;
import java.util.List;

public interface Runtime {

    BabeApiConfiguration getBabeApiConfiguration();

    List<DecodedKey> decodeSessionKeys(String sessionKeys);

    RuntimeVersion getCachedVersion();

    RuntimeVersion getVersion();

    TransactionValidationResponse validateTransaction(TransactionValidationRequest request);

    byte[] checkInherents(Block block, InherentData inherentData);

    byte[] generateSessionKeys(byte[] scaleSeed);

    byte[] getMetadata();

    void executeBlock(Block block);

    BigInteger getGenesisSlotNumber();

    /**
     * Saves the runtime instance's {@link com.limechain.trie.cache.TrieChanges} to the disk storage.
     */
    void persistsChanges();

    void close();
}
