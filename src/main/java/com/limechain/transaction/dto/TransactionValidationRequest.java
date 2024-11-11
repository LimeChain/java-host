package com.limechain.transaction.dto;

import io.emeraldpay.polkaj.types.Hash256;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
public class TransactionValidationRequest {

    private byte[] transaction;

    @Nullable
    private TransactionSource source;

    @Nullable
    private Hash256 parentBlockHash;
}
