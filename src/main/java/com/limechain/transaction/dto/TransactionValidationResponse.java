package com.limechain.transaction.dto;

import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
public class TransactionValidationResponse {

    @Nullable
    TransactionValidity validity;

    @Nullable
    TransactionValidityError validityError;
}
