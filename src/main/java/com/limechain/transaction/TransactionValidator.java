package com.limechain.transaction;

import com.limechain.exception.misc.RuntimeApiVersionException;
import com.limechain.exception.transaction.TransactionValidationException;
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
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Objects;

@Component
public class TransactionValidator {

    private final TransactionState transactionState;
    private final BlockState blockState;

    public TransactionValidator(TransactionState transactionState) {
        this.transactionState = transactionState;
        this.blockState = BlockState.getInstance();
    }

    public ValidTransaction validateExternalTransaction(Extrinsic extrinsic)
            throws TransactionValidationException {
        if (transactionState.existsInQueue(extrinsic) || transactionState.existsInPool(extrinsic)) {
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

        TransactionValidationResponse response = runtime.validateTransaction(createScaleValidationRequest(
                runtime.getCachedVersion().getApis()
                        .getApiVersion(ApiVersionName.TRANSACTION_QUEUE_API.getHashedName()),
                TransactionSource.EXTERNAL,
                header.getHash(),
                extrinsic
        ));

        if (!Objects.isNull(response.getValidityError())) {
            throw new TransactionValidationException(response.getValidityError().toString());
        }

        return new ValidTransaction(extrinsic, response.getValidity());
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
