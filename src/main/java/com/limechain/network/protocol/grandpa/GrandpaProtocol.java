package com.limechain.network.protocol.grandpa;

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
 * Handler for GRANDPA protocol messages and streams.
 */
@Log
public class GrandpaProtocol extends ProtocolHandler<GrandpaController> {
    private static final long TRAFFIC_LIMIT = Long.MAX_VALUE;

    /**
     * Create a handler with {@link Long#MAX_VALUE} traffic limit
     */
    public GrandpaProtocol() {
        super(TRAFFIC_LIMIT, TRAFFIC_LIMIT);
    }

    /**
     * Handle new initiator stream opened and add channel and notification handlers to it.
     *
     * @param stream stream opened
     * @return async controller for the stream
     */
    @NotNull
    @Override
    protected CompletableFuture<GrandpaController> onStartInitiator(Stream stream) {
        return onStartStream(stream);
    }

    /**
     * Handle new responder stream opened and add channel and notification handlers to it.
     *
     * @param stream stream opened
     * @return async controller for the stream
     */
    @NotNull
    @Override
    protected CompletableFuture<GrandpaController> onStartResponder(Stream stream) {
        return onStartStream(stream);
    }

    private CompletableFuture<GrandpaController> onStartStream(Stream stream) {
        stream.pushHandler(new Leb128LengthFrameDecoder());
        stream.pushHandler(new Leb128LengthFrameEncoder());

        stream.pushHandler(new ByteArrayEncoder());
        GrandpaProtocol.NotificationHandler handler = new GrandpaProtocol.NotificationHandler(stream);
        stream.pushHandler(handler);
        return CompletableFuture.completedFuture(handler);
    }

    /**
     * Handler for notifications received on a GRANDPA protocol.
     */
    static class NotificationHandler extends GrandpaController implements ProtocolMessageHandler<ByteBuf> {
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
            connectionManager.closeGrandpaStream(stream);
            log.log(Level.INFO, "Grandpa stream closed for peer " + stream.remotePeerId());
            ProtocolMessageHandler.super.onClosed(stream);
        }

        @Override
        public void onException(Throwable cause) {
            connectionManager.closeGrandpaStream(stream);
            if (cause != null) {
                log.log(Level.WARNING, "Grandpa Exception: " + cause.getMessage());
                cause.printStackTrace();
            } else {
                log.log(Level.WARNING, "Grandpa Exception with unknown cause");
            }
            ProtocolMessageHandler.super.onException(cause);
        }
    }
}
