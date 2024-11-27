package com.limechain.transaction;

import com.limechain.exception.misc.RuntimeApiVersionException;
import com.limechain.exception.transaction.TransactionValidationException;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.version.ApiVersionName;
import com.limechain.storage.block.BlockState;
import com.limechain.transaction.dto.Extrinsic;
import com.limechain.transaction.dto.TransactionSource;
import com.limechain.transaction.dto.TransactionValidationRequest;
import com.limechain.transaction.dto.TransactionValidationResponse;
import com.limechain.transaction.dto.ValidTransaction;
import io.emeraldpay.polkaj.types.Hash256;
import io.libp2p.core.PeerId;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Log
@Component
public class TransactionProcessor {

    private final TransactionState transactionState;
    private final BlockState blockState;

    public TransactionProcessor(TransactionState transactionState) {
        this.transactionState = transactionState;
        this.blockState = BlockState.getInstance();
    }

    public void handleExternalTransactions(Extrinsic[] extrinsics, PeerId peerId) {
        for (Extrinsic current : extrinsics) {

            try {
                handleSingleExternalTransaction(current, peerId);
            } catch (TransactionValidationException e) {
                log.fine("Error when validating transaction " + current.toString()
                        + " from protocol: " + e.getMessage());
            }
        }
    }

    // Returns the hash of the extrinsic on success or propagates TransactionValidationException on failure.
    public byte[] handleSingleExternalTransaction(Extrinsic extrinsic, PeerId peerId) {
        return processTransaction(extrinsic, peerId);
    }

    // Removes any transaction that was included in the new block, also revalidate the transactions in the pool and
    // add them to the queue if necessary
    public synchronized void maintainTransactionPool(Block block) {
        if (!transactionState.isInitialized()) return;

        List<Extrinsic> newBlockExtrinsics = block.getBody().getExtrinsics();
        for (Extrinsic extrinsic : newBlockExtrinsics) {
            transactionState.removeExtrinsic(extrinsic);
        }

        Set<Extrinsic> extrinsicSet = Arrays.stream(transactionState.pendingInPool())
                .map(ValidTransaction::getExtrinsic)
                .collect(Collectors.toSet());

        for (Extrinsic extrinsic : extrinsicSet) {

            TransactionValidationResponse response;
            try {
                response = validateExternalTransaction(extrinsic, Boolean.FALSE);

                if (!Objects.isNull(response.getValidityError())) {
                    // A validity error does not always indicate that the extrinsic should be dropped from the pool.
                    // We must also check the 'shouldReject' flag.
                    if (response.getValidityError().shouldReject()) {
                        transactionState.removeExtrinsicFromPool(extrinsic);
                    }
                    continue;
                }

                // If no validity error object is present, the extrinsic is removed from the pool and added to the queue.
                var validTransaction = new ValidTransaction(extrinsic, response.getValidity());
                if (transactionState.shouldAddToQueue(validTransaction)) {
                    transactionState.removeExtrinsicFromPool(extrinsic);
                    transactionState.pushTransaction(validTransaction);
                }

            } catch (TransactionValidationException e) {
                // If an exception is thrown, no action is taken because maintainTransactionPool is invoked frequently,
                // and the failed validation will probably succeed in a subsequent execution.
                log.fine("Error during transaction validation while maintaining the pool "
                        + extrinsic.toString() + e.getMessage());
            }
        }
    }

    private byte[] processTransaction(Extrinsic extrinsic, PeerId peerId) {
        TransactionValidationResponse response = validateExternalTransaction(extrinsic, Boolean.TRUE);

        ValidTransaction validTransaction = new ValidTransaction(
                extrinsic,
                response.getValidityError() == null ? response.getValidity() : null
        );

        if (peerId != null) {
            validTransaction.getIgnore().add(peerId);
        }

        if (Objects.nonNull(response.getValidityError())) {
            if (response.getValidityError().shouldReject()) {
                throw new TransactionValidationException(response.getValidityError().toString());
            }
            // Validity error where shouldReject is false means adding the transaction directly to the pool
            return transactionState.addToPool(validTransaction);
        }

        return transactionState.shouldAddToQueue(validTransaction)
                ? transactionState.pushTransaction(validTransaction)
                : transactionState.addToPool(validTransaction);
    }

    private TransactionValidationResponse validateExternalTransaction(Extrinsic extrinsic,
                                                                      boolean validateExistenceInState) {

        if (!transactionState.isInitialized()) {
            throw new TransactionValidationException("Transaction state is not initialized.");
        }

        if (validateExistenceInState &&
                (transactionState.existsInQueue(extrinsic) || transactionState.existsInPool(extrinsic))) {
            throw new TransactionValidationException("Transaction already validated.");
        }

        final BlockHeader header = blockState.bestBlockHeader();
        if (header == null) {
            throw new TransactionValidationException("No best block header found while validating.");
        }

        final Runtime runtime = blockState.getRuntime(header.getHash());
        if (runtime == null) {
            // This should be an unreachable state, but the blockState method has a return null.
            throw new TransactionValidationException("No runtime found for block header " + header.getHash()
                    + " while validating.");
        }

        return runtime.validateTransaction(createScaleValidationRequest(
                runtime.getCachedVersion().getApis()
                        .getApiVersion(ApiVersionName.TRANSACTION_QUEUE_API.getHashedName()),
                TransactionSource.EXTERNAL,
                header.getHash(),
                extrinsic
        ));
    }

    private static TransactionValidationRequest createScaleValidationRequest(BigInteger txQueueVersion,
                                                                             TransactionSource source,
                                                                             Hash256 hash256,
                                                                             Extrinsic transaction) {
        TransactionValidationRequest request = new TransactionValidationRequest();

        switch (txQueueVersion.intValue()) {
            case 1 -> request.setTransaction(transaction.getData());
            case 2 -> {
                request.setSource(source);
                request.setTransaction(transaction.getData());
            }
            case 3 -> {
                request.setSource(source);
                request.setTransaction(transaction.getData());
                request.setParentBlockHash(hash256);
            }
            default -> throw new RuntimeApiVersionException("Invalid transaction queue version: " + txQueueVersion);
        }

        return request;
    }
}
