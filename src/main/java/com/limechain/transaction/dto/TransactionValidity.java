package com.limechain.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * Represents the data returned by the runtime "validate_transaction" call. It provides various data needed when adding
 * the transaction to either the transaction queue or the pool. The simplest example is the priority field. The runtime
 * returns a priority value that is compared to other priorities in a reverse natural order fashion (higher number
 * value is added on top of the queue).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionValidity {

    private BigInteger priority;
    private byte[][] requires;
    private byte[][] provides;
    private BigInteger longevity;
    private Boolean propagate;
}
