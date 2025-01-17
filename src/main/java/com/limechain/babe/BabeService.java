package com.limechain.babe;

import com.limechain.babe.coordinator.SlotChangeEvent;
import com.limechain.babe.coordinator.SlotChangeListener;
import com.limechain.babe.dto.InherentData;
import com.limechain.babe.dto.InherentType;
import com.limechain.babe.dto.Slot;
import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.predigest.scale.PreDigestWriter;
import com.limechain.babe.state.EpochState;
import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.exception.misc.BabeGenericException;
import com.limechain.exception.misc.KeyStoreException;
import com.limechain.exception.storage.BlockStorageGenericException;
import com.limechain.exception.transaction.ApplyExtrinsicException;
import com.limechain.network.protocol.warp.DigestHelper;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockBody;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.ConsensusEngine;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.RuntimeBuilder;
import com.limechain.state.StateManager;
import com.limechain.storage.block.BlockHandler;
import com.limechain.storage.block.state.BlockState;
import com.limechain.storage.crypto.KeyStore;
import com.limechain.storage.crypto.KeyType;
import com.limechain.transaction.TransactionState;
import com.limechain.transaction.dto.ApplyExtrinsicResult;
import com.limechain.transaction.dto.Extrinsic;
import com.limechain.transaction.dto.ExtrinsicArray;
import com.limechain.transaction.dto.InvalidTransactionType;
import com.limechain.transaction.dto.TransactionValidityError;
import com.limechain.transaction.dto.ValidTransaction;
import com.limechain.utils.async.AsyncExecutor;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.writer.UInt64Writer;
import io.emeraldpay.polkaj.schnorrkel.Schnorrkel;
import lombok.extern.java.Log;
import org.apache.commons.collections4.map.HashedMap;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Log
@Component
public class BabeService implements SlotChangeListener {

    private final StateManager stateManager;
    private final KeyStore keyStore;
    private final AsyncExecutor asyncExecutor;
    private final Map<BigInteger, BabePreDigest> slotToPreRuntimeDigest = new HashedMap<>();
    private final RuntimeBuilder runtimeBuilder;
    private final BlockHandler blockHandler;

    public BabeService(StateManager stateManager,
                       KeyStore keyStore,
                       RuntimeBuilder runtimeBuilder,
                       BlockHandler blockHandler) {
        this.stateManager = stateManager;
        this.keyStore = keyStore;
        this.runtimeBuilder = runtimeBuilder;
        asyncExecutor = AsyncExecutor.withSingleThread();
        this.blockHandler = blockHandler;
    }

    private void executeEpochLottery(BigInteger epochIndex) {
        EpochState epochState = stateManager.getEpochState();
        var epochStartSlotNumber = epochState.getEpochStartSlotNumber(epochIndex);
        var epochEndSlotNumber = epochStartSlotNumber.add(epochState.getEpochLength());

        for (BigInteger slot = epochStartSlotNumber;
             slot.compareTo(epochEndSlotNumber) < 0;
             slot = slot.add(BigInteger.ONE)) {
            BabePreDigest babePreDigest = Authorship.claimSlot(epochState, slot, keyStore);
            if (babePreDigest != null) {
                slotToPreRuntimeDigest.put(slot, babePreDigest);
            }
        }
    }

    private void handleSlot(Slot slot, BabePreDigest preDigest) {
        log.fine(String.format("Producing block for slot %s in epoch %s.",
                slot.getNumber(), slot.getEpochIndex()));

        Block block;
        try {
            BlockHeader parentHeader = getParentBlockHeader(slot.getNumber());
            block = produceBlock(parentHeader, slot, preDigest);
        } catch (Exception e) {
            log.warning(String.format("Exception producing block: %s", e.getMessage()));
            return;
        }

        blockHandler.handleProducedBlock(block);
    }

    private Block produceBlock(BlockHeader parentHeader, Slot slot, BabePreDigest preDigest) {
        BlockHeader newBlockHeader = new BlockHeader();
        newBlockHeader.setParentHash(parentHeader.getHash());
        newBlockHeader.setBlockNumber(slot.getNumber().add(BigInteger.ONE));

        BlockState blockState = stateManager.getBlockState();
        Runtime runtime = blockState.getRuntime(parentHeader.getHash());
        Runtime newRuntime = runtimeBuilder.copyRuntime(runtime);
        newRuntime.initializeBlock(newBlockHeader);

        log.fine("Initialized block via runtime call.");

        ExtrinsicArray inherents = produceBlockInherents(slot, newRuntime);
        log.fine("Finished with inherents for block.");

        List<ValidTransaction> transactions = produceBlockTransactions(slot, newRuntime);
        log.fine("Finished with extrinsics for block.");

        BlockHeader finalizedHeader;
        try {
            finalizedHeader = newRuntime.finalizeBlock();
        } catch (Exception e) {
            transactions.forEach(stateManager.getTransactionState()::pushTransaction);
            throw new BabeGenericException("Block finalization failed. Pushed transaction back to queue.");
        }
        log.fine("Finished with finalizing block.");

        finalizedHeader.setDigest(produceDigests(finalizedHeader, preDigest));
        log.fine("Finished with digests for block.");

        List<Extrinsic> bodyExtrinsics = new ArrayList<>(Arrays.asList(inherents.getExtrinsics()));
        bodyExtrinsics.addAll(transactions.stream()
                .map(ValidTransaction::getExtrinsic)
                .toList());

        BlockBody body = new BlockBody(bodyExtrinsics);

        blockState.storeRuntime(finalizedHeader.getHash(), newRuntime);

        return new Block(finalizedHeader, body);
    }

    private HeaderDigest[] produceDigests(BlockHeader header, BabePreDigest digest) {
        // Make a copy of the digests from the header.
        int length = header.getDigest().length;
        HeaderDigest[] newDigests = Arrays.copyOf(header.getDigest(), length + 2);

        // Setup pre-digest.
        HeaderDigest preDigest = new HeaderDigest();
        preDigest.setId(ConsensusEngine.BABE);
        preDigest.setType(DigestType.PRE_RUNTIME);
        preDigest.setMessage(ScaleUtils.Encode.encode(new PreDigestWriter(), digest));

        newDigests[length + 1] = preDigest;

        Authority auth = stateManager.getEpochState().getCurrentEpochData().getAuthorities()
                .get((int) digest.getAuthorityIndex());
        Schnorrkel.KeyPair keyPair = keyStore.getKeyPair(KeyType.BABE, auth.getPublicKey())
                .orElseThrow(() -> new KeyStoreException("No KeyPair found for provided pub key."));
        newDigests[length + 2] = DigestHelper.buildSealHeaderDigest(header, keyPair);

        return newDigests;
    }

    private List<ValidTransaction> produceBlockTransactions(Slot slot, Runtime runtime) {
        List<ValidTransaction> toAdd = new ArrayList<>();

        // Keep 1/3 of the slot duration for validating and importing block.
        Instant slotEnd = slot.getStart()
                .plus(slot.getDuration()
                        .multipliedBy(2)
                        .dividedBy(3));

        Duration timeout = Duration.between(Instant.now(), slotEnd);

        TransactionState transactionState = stateManager.getTransactionState();
        // while not End-Of-Slot
        while (timeout.isPositive()) {
            timeout = Duration.between(Instant.now(), slotEnd);
            // Next-Ready-Extrinsic
            ValidTransaction transaction = transactionState.pollTransactionWithTimer(timeout.get(ChronoUnit.MILLIS));

            boolean isTimedOut = transaction == null;
            if (isTimedOut) {
                break;
            }

            Extrinsic extrinsic = transaction.getExtrinsic();

            ApplyExtrinsicResult applyExtrinsicResponse = runtime.applyExtrinsic(extrinsic);

            if (applyExtrinsicResponse.getOutcome() != null && applyExtrinsicResponse.getOutcome().isValid()) {
                toAdd.add(transaction);
                continue;
            }

            TransactionValidityError error = applyExtrinsicResponse.getValidityError();
            if (error == null) {
                throw new ApplyExtrinsicException("Invalid state. Both outcome and transaction error are null.");
            }

            // !Should-Drop
            if (!applyExtrinsicResponse.getValidityError().shouldReject()) {
                transactionState.pushTransaction(transaction);
            }

            //Block-Is-Full
            if (InvalidTransactionType.EXHAUST_BLOCK_RESOURCES.equals(applyExtrinsicResponse.getValidityError())) {
                break;
            }
        }

        return toAdd;
    }

    private ExtrinsicArray produceBlockInherents(Slot slot, Runtime runtime) {
        InherentData inherentData = new InherentData();

        inherentData.getData().put(InherentType.TIMESTAMP0.toByteArray(),
                ScaleUtils.Encode.encode(new UInt64Writer(), BigInteger.valueOf(slot.getStart().toEpochMilli())));

        inherentData.getData().put(InherentType.BABESLOT.toByteArray(),
                ScaleUtils.Encode.encode(new UInt64Writer(), slot.getNumber()));

        // Empty till we find out what this exactly is used for.
        inherentData.getData().put(InherentType.PARACHN0.toByteArray(), new byte[]{});
        // Empty till we find out what this exactly is used for.
        inherentData.getData().put(InherentType.NEWHEADS.toByteArray(), new byte[]{});

        ExtrinsicArray inherentExtrinsics = runtime.inherentExtrinsics(inherentData);

        for (int i = 0; i < inherentExtrinsics.getExtrinsics().length; i++) {
            ApplyExtrinsicResult result = runtime.applyExtrinsic(inherentExtrinsics.getExtrinsics()[i]);
            if (result.getOutcome() != null && result.getOutcome().isValid()) {
                continue;
            }

            throw new ApplyExtrinsicException("An exception occurred when applying block inherent.");
        }

        return inherentExtrinsics;
    }

    private BlockHeader getParentBlockHeader(BigInteger slotNum) {
        BlockState blockState = stateManager.getBlockState();
        BlockHeader parentHeader = blockState.bestBlockHeader();
        if (parentHeader == null) {
            throw new BlockStorageGenericException("Could not get best block header");
        }

        boolean parentIsGenesis = blockState.getGenesisBlockHeader().getHash().equals(parentHeader.getHash());
        if (!parentIsGenesis) {
            BigInteger bestBlockSlotNum = DigestHelper.getBabePreRuntimeDigest(parentHeader.getDigest())
                    .orElseThrow(() ->
                            new BlockStorageGenericException("No pre-runtime digest found for parent block"))
                    .getSlotNumber();

            if (bestBlockSlotNum.compareTo(slotNum) > 0)
                throw new BabeGenericException(
                        String.format("Provided slot, %s, is behind parent slot, %s", bestBlockSlotNum, slotNum));

            if (bestBlockSlotNum.equals(slotNum)) {
                BlockHeader newParentHeader = blockState.getHeader(parentHeader.getParentHash());
                if (newParentHeader == null) {
                    throw new BlockStorageGenericException(
                            String.format("No parent header for block hash %s", parentHeader.getParentHash()));
                }
                parentHeader = newParentHeader;
            }
        }

        return parentHeader;
    }

    @Override
    public void slotChanged(SlotChangeEvent event) {
        Slot slot = event.getSlot();

        // Invoke-Block-Authoring
        BabePreDigest preDigest = slotToPreRuntimeDigest.get(slot.getNumber());
        if (preDigest != null) {
            asyncExecutor.executeAndForget(() -> handleSlot(slot, preDigest));
        }

        if (event.isLastSlotFromCurrentEpoch()) {
            BigInteger nextEpochIndex = slot.getEpochIndex().add(BigInteger.ONE);
            executeEpochLottery(nextEpochIndex);
        }
    }
}
