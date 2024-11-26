package com.limechain.transaction.dto;

import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
public class ApplyExtrinsicResult {

    @Nullable
    private DispatchOutcome outcome;

    @Nullable
    private TransactionValidityError validityError;
}
