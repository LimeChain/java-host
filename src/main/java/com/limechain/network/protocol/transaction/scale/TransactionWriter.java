package com.limechain.network.protocol.transaction.scale;

import com.limechain.transaction.dto.ExtrinsicArray;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TransactionWriter implements ScaleWriter<ExtrinsicArray> {

    private static final TransactionWriter INSTANCE = new TransactionWriter();

    public static TransactionWriter getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(ScaleCodecWriter writer, ExtrinsicArray holder) throws IOException {
        int length = holder.getExtrinsics().length;
        writer.writeCompact(length);
        for (int i = 0; i < length; i++) {
            writer.writeAsList(holder.getExtrinsics()[i].getData());
        }
    }
}
