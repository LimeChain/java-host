package com.limechain.transaction.dto;

import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
public class TransactionValidationResponse {

    @Nullable
    private TransactionValidity validity;

    @Nullable
    private TransactionValidityError validityError;
}
