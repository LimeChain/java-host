package com.limechain.utils.scale.writers;

import com.limechain.transaction.dto.TransactionValidationRequest;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;

import java.io.IOException;
import java.util.Objects;

public class TransactionValidationWriter implements ScaleWriter<TransactionValidationRequest> {

    @Override
    public void write(ScaleCodecWriter writer, TransactionValidationRequest request) throws IOException {
        if (!Objects.isNull(request.getSource())) {
            writer.writeByte(request.getSource().ordinal());
        }

        writer.writeAsList(request.getTransaction());

        if (!Objects.isNull(request.getParentBlockHash())) {
            writer.writeByteArray(request.getParentBlockHash().getBytes());
        }
    }
}
