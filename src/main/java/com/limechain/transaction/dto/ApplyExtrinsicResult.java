package com.limechain.transaction.dto;

import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
public class ApplyExtrinsicResult {

    @Nullable
    DispatchOutcome outcome;

    @Nullable
    TransactionValidityError validityError;
}
