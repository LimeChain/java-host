package com.limechain.utils.scale.readers;

import com.limechain.transaction.dto.TransactionValidationResponse;
import com.limechain.transaction.dto.TransactionValidity;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.scale.reader.UInt64Reader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TransactionValidationReader implements ScaleReader<TransactionValidationResponse> {

    private static final TransactionValidationReader INSTANCE = new TransactionValidationReader();

    public static TransactionValidationReader getInstance() {
        return INSTANCE;
    }

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
            response.setValidityError(TransactionValidityErrorReader.getInstance().read(reader));
        }

        return response;
    }
}
