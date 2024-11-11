package com.limechain.network.protocol.transaction.scale;

import com.limechain.transaction.dto.ExtrinsicArray;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;

import java.io.IOException;

public class TransactionWriter implements ScaleWriter<ExtrinsicArray> {

    @Override
    public void write(ScaleCodecWriter writer, ExtrinsicArray holder) throws IOException {
        int length = holder.getExtrinsics().length;
        writer.writeCompact(length);
        for (int i = 0; i < length; i++) {
            writer.writeAsList(holder.getExtrinsics()[i].getData());
        }
    }
}
