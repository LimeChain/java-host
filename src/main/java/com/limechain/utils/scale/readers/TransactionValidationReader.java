package com.limechain.utils.scale.readers;

import com.limechain.transaction.dto.InvalidTransactionType;
import com.limechain.transaction.dto.TransactionValidationResponse;
import com.limechain.transaction.dto.TransactionValidity;
import com.limechain.transaction.dto.TransactionValidityError;
import com.limechain.transaction.dto.UnknownTransactionType;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;

public class TransactionValidationReader implements ScaleReader<TransactionValidationResponse> {

    private static final int INVALID_TRANSACTION_TYPE = 0;

    @Override
    public TransactionValidationResponse read(ScaleCodecReader reader) {
        TransactionValidationResponse response = new TransactionValidationResponse();

        if (ScaleUtils.isScaleResultSuccessful(reader)) {
            TransactionValidity validity = new TransactionValidity();

            validity.setPriority(new UInt64Reader().read(reader));

            int requiresCount = reader.readCompactInt();
            byte[][] requires = new byte[requiresCount][];
            for (int i = 0; i < requiresCount; i++) {
                requires[i] = reader.readByteArray();
            }
            validity.setRequires(requires);

            int providesCount = reader.readCompactInt();
            byte[][] provides = new byte[providesCount][];
            for (int i = 0; i < providesCount; i++) {
                provides[i] = reader.readByteArray();
            }
            validity.setProvides(provides);

            validity.setLongevity(new UInt64Reader().read(reader));
            validity.setPropagate(reader.readUByte() != 0);

            response.setValidity(validity);
        } else {
            int errorType = reader.readUByte();
            int errorInt = reader.readUByte();
            TransactionValidityError error = errorType == INVALID_TRANSACTION_TYPE
                    ? InvalidTransactionType.getFromInt(errorInt)
                    : UnknownTransactionType.getFromInt(errorInt);

            response.setValidityError(error);
        }

        return response;
    }
}
