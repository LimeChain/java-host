package com.limechain.network.protocol.transaction;

import com.limechain.network.ConnectionManager;
import com.limechain.network.encoding.Leb128LengthFrameDecoder;
import com.limechain.network.encoding.Leb128LengthFrameEncoder;
import io.libp2p.core.Stream;
import io.libp2p.protocol.ProtocolHandler;
import io.libp2p.protocol.ProtocolMessageHandler;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Handler for Transactions protocol messages and streams.
 */
@Log
public class TransactionsProtocol extends ProtocolHandler<TransactionController> {
    private static final long TRAFFIC_LIMIT = Long.MAX_VALUE;

    /**
     * Creates a handler with {@link Long#MAX_VALUE} traffic limit.
     * This is a global decreasing limit for the protocol, that gets reduced by the size of each message.
     * In the future it should be changed to a per-message limit
     */
    public TransactionsProtocol() {
        super(TRAFFIC_LIMIT, TRAFFIC_LIMIT);
    }

    /**
     * Handles a new opened initiator stream and adds channel and notification handlers to it.
     *
     * @param stream stream opened
     * @return async controller for the stream
     */
    @NotNull
    @Override
    protected CompletableFuture<TransactionController> onStartInitiator(Stream stream) {
        return onStartStream(stream);
    }

    /**
     * Handles a new opened responder stream and adds channel and notification handlers to it.
     *
     * @param stream stream opened
     * @return async controller for the stream
     */
    @NotNull
    @Override
    protected CompletableFuture<TransactionController> onStartResponder(Stream stream) {
        return onStartStream(stream);
    }

    private CompletableFuture<TransactionController> onStartStream(Stream stream) {
        stream.pushHandler(new Leb128LengthFrameDecoder());
        stream.pushHandler(new Leb128LengthFrameEncoder());

        stream.pushHandler(new ByteArrayEncoder());
        TransactionsProtocol.NotificationHandler handler = new TransactionsProtocol.NotificationHandler(stream);
        stream.pushHandler(handler);
        return CompletableFuture.completedFuture(handler);
    }

    /**
     * Handler for notifications received on the Transactions protocol.
     */
    static class NotificationHandler extends TransactionController implements ProtocolMessageHandler<ByteBuf> {
        ConnectionManager connectionManager = ConnectionManager.getInstance();

        public NotificationHandler(Stream stream) {
            super(stream);
        }

        @Override
        public void onMessage(@NotNull Stream stream, ByteBuf msg) {
            byte[] messageBytes = new byte[msg.readableBytes()];
            msg.readBytes(messageBytes);
            engine.receiveRequest(messageBytes, stream.remotePeerId(), stream);
        }

        @Override
        public void onClosed(Stream stream) {
            connectionManager.closeTransactionsStream(stream);
            log.log(Level.INFO, "Transactions stream closed for peer " + stream.remotePeerId());
            ProtocolMessageHandler.super.onClosed(stream);
        }

        @Override
        public void onException(Throwable cause) {
            connectionManager.closeTransactionsStream(stream);
            if (cause != null) {
                log.log(Level.WARNING, "Transactions Exception: " + cause.getMessage());
            } else {
                log.log(Level.WARNING, "Transactions Exception with unknown cause");
            }
            ProtocolMessageHandler.super.onException(cause);
        }
    }
}
