package com.limechain.network.protocol.transaction;

import com.limechain.exception.scale.ScaleEncodingException;
import com.limechain.exception.transaction.TransactionValidationException;
import com.limechain.network.ConnectionManager;
import com.limechain.network.protocol.transaction.scale.TransactionReader;
import com.limechain.network.protocol.transaction.scale.TransactionWriter;
import com.limechain.rpc.server.AppBean;
import com.limechain.sync.warpsync.WarpSyncState;
import com.limechain.transaction.TransactionState;
import com.limechain.transaction.TransactionValidator;
import com.limechain.transaction.dto.Extrinsic;
import com.limechain.transaction.dto.ExtrinsicArray;
import com.limechain.transaction.dto.ValidTransaction;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import lombok.extern.java.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Engine for handling transactions on Transactions streams.
 */
@Log
public class TransactionEngine {

    private static final int HANDSHAKE_LENGTH = 1;

    private final ConnectionManager connectionManager;
    private final TransactionState transactionState;
    private final TransactionValidator transactionValidator;

    public TransactionEngine() {
        connectionManager = ConnectionManager.getInstance();
        transactionState = AppBean.getBean(TransactionState.class);
        transactionValidator = AppBean.getBean(TransactionValidator.class);
    }

    /**
     * Handles an incoming request as follows:
     *
     * <p><b>On streams we initiated:</b>  adds streams, where we receive a handshake message,
     * to initiator streams in peer's {@link com.limechain.network.dto.PeerInfo}, ignores all other message types.
     *
     * <p><b>On responder stream: </b>
     * <p>If message payload contains a valid handshake, adds the stream when the peer is not connected already,
     * ignore otherwise. </p>
     * <p>On transactions messages {@link WarpSyncState}: </p>
     * <p>Logs and ignores other message types.</p>
     *
     * @param message received message as byre array
     * @param peerId  peer id of sender
     * @param stream  stream, where the request was received
     */
    public void receiveRequest(byte[] message, PeerId peerId, Stream stream) {
        if (message == null) {
            log.log(Level.WARNING,
                    String.format("Transactions message is null from Peer %s", peerId));
            return;
        }
        log.log(Level.INFO, "Transaction message length:" + message.length);

        if (stream.isInitiator()) {
            handleInitiatorStreamMessage(message, stream);
        } else {
            handleResponderStreamMessage(message, stream);
        }
    }

    private void handleInitiatorStreamMessage(byte[] message, Stream stream) {
        PeerId peerId = stream.remotePeerId();
        if (!isHandshake(message)) {
            stream.close();
            log.log(Level.WARNING, "Non handshake message on initiator transactions stream from peer " + peerId);
            return;
        }
        connectionManager.addTransactionsStream(stream);
        log.log(Level.INFO, "Received transactions handshake from " + peerId);
        //TODO Replace empty transaction messages once we have validation working.
        stream.writeAndFlush(new byte[]{});
    }

    private void handleResponderStreamMessage(byte[] message, Stream stream) {
        PeerId peerId = stream.remotePeerId();
        boolean connectedToPeer = connectionManager.isTransactionsConnected(peerId);

        if (!connectedToPeer && !isHandshake(message)) {
            log.log(Level.WARNING, "No handshake for transactions message from Peer " + peerId);
            stream.close();
            return;
        }

        if (isHandshake(message)) {
            handleHandshake(peerId, stream);
        } else {
            handleTransactionMessage(message, peerId);
        }
    }

    private void handleHandshake(PeerId peerId, Stream stream) {
        if (connectionManager.isTransactionsConnected(peerId)) {
            log.log(Level.INFO, "Received existing transactions handshake from " + peerId);
            stream.close();
        } else {
            connectionManager.addTransactionsStream(stream);
            log.log(Level.INFO, "Received transactions handshake from " + peerId);
        }
        writeHandshakeToStream(stream, peerId);
    }

    private void handleTransactionMessage(byte[] message, PeerId peerId) {
        ScaleCodecReader reader = new ScaleCodecReader(message);
        ExtrinsicArray transactions = reader.read(new TransactionReader());
        log.log(Level.INFO, "Received " + transactions.getExtrinsics().length + " transactions from Peer "
                + peerId);

        for (int i = 0; i < transactions.getExtrinsics().length; i++) {
            Extrinsic current = transactions.getExtrinsics()[i];

            ValidTransaction validTransaction;
            try {
                validTransaction = transactionValidator.validateExternalTransaction(current);
                validTransaction.getIgnore().add(peerId);
            } catch (TransactionValidationException e) {
                log.warning("Error when validating transaction " + current.toString()
                        + " from protocol: " + e.getMessage());
                continue;
            }

            if (transactionState.shouldAddToQueue(validTransaction)) {
                transactionState.pushTransaction(validTransaction);
            } else {
                transactionState.addToPool(validTransaction);
            }
        }
    }

    /**
     * Send our Transactions handshake on a given <b>initiator</b> stream.
     *
     * @param stream <b>initiator</b> stream to write the message to
     * @param peerId peer to send to
     */
    public void writeHandshakeToStream(Stream stream, PeerId peerId) {
        byte[] handshake = new byte[]{};

        log.log(Level.INFO, "Sending transactions handshake to " + peerId);
        stream.writeAndFlush(handshake);
    }

    /**
     * Send our Transactions message from {@link WarpSyncState} on a given <b>responder</b> stream.
     *
     * @param stream <b>responder</b> stream to write the message to
     * @param peerId peer to send to
     */
    public void writeTransactionsMessage(Stream stream, PeerId peerId) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        //TODO Replace empty transaction messages once we have validation working.
        try (ScaleCodecWriter writer = new ScaleCodecWriter(buf)) {
            writer.write(new TransactionWriter(), new ExtrinsicArray(new Extrinsic[]{
                    new Extrinsic(new byte[]{}), new Extrinsic(new byte[]{})
            }));
        } catch (IOException e) {
            throw new ScaleEncodingException(e);
        }

        log.log(Level.INFO, "Sending transaction message to peer " + peerId);
        //TODO send transaction message containing non repetitive transactions for peer.
    }

    private boolean isHandshake(byte[] message) {
        return message.length == HANDSHAKE_LENGTH;
    }
}
