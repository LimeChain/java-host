package com.limechain.network.protocol.transaction.scale;

import com.limechain.transaction.dto.Extrinsic;
import com.limechain.transaction.dto.ExtrinsicArray;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;

public class TransactionReader implements ScaleReader<ExtrinsicArray> {

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
