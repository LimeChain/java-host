package com.limechain.network.protocol.transaction;

import io.libp2p.core.Stream;

/**
 * A controller for sending message on a Transactions stream.
 */
public class TransactionController {
    protected final TransactionEngine engine = new TransactionEngine();
    protected final Stream stream;

    public TransactionController(Stream stream) {
        this.stream = stream;
    }

    /**
     * Sends a handshake message over the controller stream.
     */
    public void sendHandshake() {
        engine.writeHandshakeToStream(stream, stream.remotePeerId());
    }

    /**
     * Sends a neighbour message over the controller stream.
     */
    public void sendTransactionsMessage(byte[] encodedTransactionMessage) {
        engine.writeTransactionsMessage(stream, stream.remotePeerId(), encodedTransactionMessage);
    }
}
