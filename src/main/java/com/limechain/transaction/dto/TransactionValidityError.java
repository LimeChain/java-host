package com.limechain.transaction.dto;

public interface TransactionValidityError {

    boolean shouldReject();
}
