package com.limechain.runtime;

import com.limechain.babe.api.BabeApiConfiguration;
import com.limechain.babe.api.BlockEquivocationProof;
import com.limechain.babe.api.OpaqueKeyOwnershipProof;
import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.rpc.methods.author.dto.DecodedKey;
import com.limechain.runtime.version.RuntimeVersion;
import com.limechain.sync.fullsync.inherents.InherentData;
import com.limechain.transaction.dto.ApplyExtrinsicResult;
import com.limechain.transaction.dto.Extrinsic;
import com.limechain.transaction.dto.ExtrinsicArray;
import com.limechain.transaction.dto.TransactionValidationRequest;
import com.limechain.transaction.dto.TransactionValidationResponse;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public interface Runtime {

    BabeApiConfiguration getBabeApiConfiguration();

    Optional<OpaqueKeyOwnershipProof> generateKeyOwnershipProof(BigInteger slotNumber, byte[] authorityPublicKey);

    void submitReportEquivocationUnsignedExtrinsic(BlockEquivocationProof blockEquivocationProof, byte[] keyOwnershipProof);

    List<DecodedKey> decodeSessionKeys(String sessionKeys);

    RuntimeVersion getCachedVersion();

    RuntimeVersion getVersion();

    TransactionValidationResponse validateTransaction(TransactionValidationRequest request);

    BlockHeader finalizeBlock();

    byte[] checkInherents(Block block, InherentData inherentData);

    ApplyExtrinsicResult applyExtrinsic(Extrinsic extrinsic);

    ExtrinsicArray inherentExtrinsics(com.limechain.babe.dto.InherentData inherentData);

    byte[] generateSessionKeys(byte[] scaleSeed);

    byte[] getMetadata();

    void executeBlock(Block block);

    void initializeBlock(BlockHeader blockHeader);

    BigInteger getGenesisSlotNumber();

    /**
     * Saves the runtime instance's {@link com.limechain.trie.cache.TrieChanges} to the disk storage.
     */
    void persistsChanges();

    void close();

    List<Authority> getGrandpaApiAuthorities();

}
