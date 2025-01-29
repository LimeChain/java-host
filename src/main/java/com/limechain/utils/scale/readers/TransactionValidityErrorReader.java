package com.limechain.utils.scale.readers;

import com.limechain.transaction.dto.InvalidTransactionType;
import com.limechain.transaction.dto.TransactionValidityError;
import com.limechain.transaction.dto.UnknownTransactionType;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TransactionValidityErrorReader implements ScaleReader<TransactionValidityError> {

    private static final TransactionValidityErrorReader INSTANCE = new TransactionValidityErrorReader();
    private static final int INVALID_TRANSACTION_TYPE = 0;

    public static TransactionValidityErrorReader getInstance() {
        return INSTANCE;
    }

    @Override
    public TransactionValidityError read(ScaleCodecReader scaleCodecReader) {
        int errorType = scaleCodecReader.readUByte();
        int errorInt = scaleCodecReader.readUByte();

        return errorType == INVALID_TRANSACTION_TYPE
                ? InvalidTransactionType.getFromInt(errorInt)
                : UnknownTransactionType.getFromInt(errorInt);
    }
}
