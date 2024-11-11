package com.limechain.network.protocol.transaction;

import com.limechain.network.StrictProtocolBinding;

/**
 * Transactions protocol binding
 */
public class TransactionMessages extends StrictProtocolBinding<TransactionController> {
    public TransactionMessages(String protocolId, TransactionsProtocol protocol) {
        super(protocolId, protocol);
    }
}
