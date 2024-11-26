package com.limechain.utils.scale.readers;

import com.limechain.transaction.dto.InvalidTransactionType;
import com.limechain.transaction.dto.TransactionValidityError;
import com.limechain.transaction.dto.UnknownTransactionType;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;

public class TransactionValidityErrorReader implements ScaleReader<TransactionValidityError> {

    private static final int INVALID_TRANSACTION_TYPE = 0;

    @Override
    public TransactionValidityError read(ScaleCodecReader scaleCodecReader) {
        int errorType = scaleCodecReader.readUByte();
        int errorInt = scaleCodecReader.readUByte();

        return errorType == INVALID_TRANSACTION_TYPE
                ? InvalidTransactionType.getFromInt(errorInt)
                : UnknownTransactionType.getFromInt(errorInt);
    }
}
