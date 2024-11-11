package com.limechain.transaction.dto;

/**
 * The source of the transaction.
 */
public enum TransactionSource {
    /**
     * Transaction is already included in a block.
     * <p>
     * This means that we can't really tell where the transaction is coming from,
     * since it's already in the received block. Note that the custom validation logic
     * using either `Local` or `External` should most likely just allow `InBlock`
     * transactions as well.
     */
    IN_BLOCK,

    /**
     * Transaction is coming from a local source.
     * <p>
     * This means that the transaction was produced internally by the node
     * (for instance an Off-Chain Worker or an Off-Chain Call), as opposed
     * to being received over the network.
     */
    LOCAL,

    /**
     * Transaction has been received externally.
     * <p>
     * This means the transaction has been received from (usually) an "untrusted" source,
     * for instance received over the network or RPC.
     */
    EXTERNAL;
}
