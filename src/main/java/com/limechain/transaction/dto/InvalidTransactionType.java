package com.limechain.transaction.dto;

import org.springframework.lang.Nullable;

import java.util.Arrays;

public enum InvalidTransactionType implements TransactionValidityError {

    CALL_NOT_EXPECTED(0, true),
    INABILITY_TO_PAY_FEES(1, true),
    TRANSACTION_NOT_YET_VALID(2, false),
    TRANSACTION_OUTDATED(3, true),
    INVALID_PROOF(4, true),
    ANCIENT_BIRTH_BLOCK(5, true),
    EXHAUST_BLOCK_RESOURCES(6, false),
    UNKNOWN_ERROR(7, true),
    MANDATORY_DISPATCH_ERROR(8, true),
    INVALID_MANDATORY_DISPATCH(9, true);

    private final int id;
    private final boolean shouldReject;

    InvalidTransactionType(int id, boolean shouldReject) {
        this.id = id;
        this.shouldReject = shouldReject;
    }

    @Nullable
    public static TransactionValidityError getFromInt(int intValue) {
        return Arrays.stream(values())
                .filter(v -> v.id == intValue)
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean shouldReject() {
        return shouldReject;
    }
}
