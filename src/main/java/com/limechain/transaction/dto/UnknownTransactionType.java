package com.limechain.transaction.dto;

import org.springframework.lang.Nullable;

import java.util.Arrays;

public enum UnknownTransactionType implements TransactionValidityError {

    METADATA_SEARCH_FAILURE(0, true),
    NO_VALIDATOR_FOUND(1, true),
    UNKNOWN_VALIDITY(2, false);

    private final int id;
    private final boolean shouldReject;

    UnknownTransactionType(int id, boolean shouldReject) {
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
