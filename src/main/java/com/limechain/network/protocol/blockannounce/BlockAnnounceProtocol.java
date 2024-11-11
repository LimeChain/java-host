package com.limechain.network.protocol.blockannounce;

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

@Log
public class BlockAnnounceProtocol extends ProtocolHandler<BlockAnnounceController> {
    public static final int MAX_HANDSHAKE_SIZE = 1024 * 1024;
    public static final int MAX_NOTIFICATION_SIZE = 1024 * 1024;

    public BlockAnnounceProtocol() {
        super(MAX_HANDSHAKE_SIZE, MAX_NOTIFICATION_SIZE);
    }

    @NotNull
    @Override
    protected CompletableFuture<BlockAnnounceController> onStartInitiator(Stream stream) {
        stream.pushHandler(new Leb128LengthFrameDecoder());
        stream.pushHandler(new Leb128LengthFrameEncoder());

        stream.pushHandler(new ByteArrayEncoder());
        NotificationHandler handler = new NotificationHandler(stream);
        stream.pushHandler(handler);
        return CompletableFuture.completedFuture(handler);
    }

    @NotNull
    @Override
    protected CompletableFuture<BlockAnnounceController> onStartResponder(Stream stream) {
        stream.pushHandler(new Leb128LengthFrameDecoder());
        stream.pushHandler(new Leb128LengthFrameEncoder());

        stream.pushHandler(new ByteArrayEncoder());
        NotificationHandler handler = new NotificationHandler(stream);
        stream.pushHandler(handler);
        return CompletableFuture.completedFuture(handler);
    }

    static class NotificationHandler extends BlockAnnounceController implements ProtocolMessageHandler<ByteBuf> {
        ConnectionManager connectionManager = ConnectionManager.getInstance();
        public NotificationHandler(Stream stream) {
            super(stream);
        }

        @Override
        public void onMessage(@NotNull Stream stream, ByteBuf msg) {
            byte[] messageBytes = new byte[msg.readableBytes()];
            msg.readBytes(messageBytes);
            engine.receiveRequest(messageBytes, stream);
        }

        @Override
        public void onClosed(@NotNull Stream stream) {
            connectionManager.closeBlockAnnounceStream(stream);
            log.log(Level.INFO, "Block announce stream closed for peer " + stream.remotePeerId());
            ProtocolMessageHandler.super.onClosed(stream);
        }

        @Override
        public void onException(Throwable cause) {
            connectionManager.closeBlockAnnounceStream(stream);
            if (cause != null) {
                log.log(Level.WARNING, "Block Announce Exception: " + cause.getMessage());
                cause.printStackTrace();
            } else {
                log.log(Level.WARNING, "Block Announce Exception with unknown cause");
            }
            ProtocolMessageHandler.super.onException(cause);
        }
    }
}
