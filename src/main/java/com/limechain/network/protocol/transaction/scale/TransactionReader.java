package com.limechain.network.protocol.transaction.scale;

import com.limechain.transaction.dto.Extrinsic;
import com.limechain.transaction.dto.ExtrinsicArray;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TransactionReader implements ScaleReader<ExtrinsicArray> {

    private static final TransactionReader INSTANCE = new TransactionReader();

    public static TransactionReader getInstance() {
        return INSTANCE;
    }

    @Override
    public ExtrinsicArray read(ScaleCodecReader reader) {
        int size = reader.readCompactInt();
        Extrinsic[] transactions = new Extrinsic[size];
        for (int i = 0; i < size; i++) {
            transactions[i] = new Extrinsic(reader.readByteArray());
        }
        return new ExtrinsicArray(transactions);
    }
}
