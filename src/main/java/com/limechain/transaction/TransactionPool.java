package com.limechain.transaction;

import com.limechain.transaction.dto.Extrinsic;
import com.limechain.transaction.dto.ValidTransaction;
import com.limechain.utils.HashUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class TransactionPool {

    private final Map<String, ValidTransaction> transactions;

    public TransactionPool() {
        transactions = new HashMap<>();
    }

    public ValidTransaction get(Extrinsic extrinsic) {
        byte[] key = HashUtils.hashWithBlake2b(extrinsic.getData());
        return transactions.get(Arrays.toString(key));
    }

    public ValidTransaction[] transactions() {
        return transactions.values().toArray(ValidTransaction[]::new);
    }

    public byte[] insert(ValidTransaction validTransaction) {
        byte[] key = HashUtils.hashWithBlake2b(validTransaction.getExtrinsic().getData());
        transactions.put(Arrays.toString(key), validTransaction);
        return key;
    }

    public void removeExtrinsic(Extrinsic extrinsic) {
        byte[] key = HashUtils.hashWithBlake2b(extrinsic.getData());
        transactions.remove(Arrays.toString(key));
    }

    public int length() {
        return transactions.size();
    }

    public boolean exists(Extrinsic extrinsic) {
        byte[] key = HashUtils.hashWithBlake2b(extrinsic.getData());
        return transactions.containsKey(Arrays.toString(key));
    }
}
