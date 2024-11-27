package com.limechain.network.protocol.blockannounce;

import com.limechain.babe.BlockProductionVerifier;
import com.limechain.network.ConnectionManager;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceHandshake;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceHandshakeBuilder;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.blockannounce.scale.BlockAnnounceHandshakeScaleWriter;
import com.limechain.network.protocol.blockannounce.scale.BlockAnnounceMessageScaleReader;
import com.limechain.network.protocol.warp.DigestHelper;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.sync.warpsync.WarpSyncState;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unused")
@ExtendWith(MockitoExtension.class)
class BlockAnnounceEngineTest {
    @InjectMocks
    private BlockAnnounceEngine blockAnnounceEngine;

    @Mock
    private Stream stream;
    @Mock
    private PeerId peerId;

    @Mock
    private ConnectionManager connectionManager;

    @Mock
    private WarpSyncState warpSyncState;

    @Mock
    private DigestHelper digestHelper;

    @Mock
    private BlockAnnounceHandshake handshake;
    @Mock
    private BlockAnnounceHandshakeBuilder handshakeBuilder;
    @Mock
    private BlockProductionVerifier verifier;

    @Test
    void receiveNonHandshakeRequestWhenNotConnectedShouldIgnore() {
        byte[] message = { 1, 2, 3 };
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isBlockAnnounceConnected(peerId)).thenReturn(false);

        blockAnnounceEngine.receiveRequest(message, stream);

        verifyNoMoreInteractions(connectionManager);
        verifyNoInteractions(warpSyncState);
    }

    @Test
    void receiveHandshakeRequestWhenNotConnectedShouldAddStreamToConnection() {
        byte[] message = new  byte[BlockAnnounceEngine.HANDSHAKE_LENGTH];
        Arrays.fill(message, (byte) 1);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isBlockAnnounceConnected(peerId)).thenReturn(false);
        when(handshakeBuilder.getBlockAnnounceHandshake()).thenReturn(handshake);
        try (
                MockedConstruction<ScaleCodecReader> readerMock = mockConstruction(ScaleCodecReader.class,
                (mock, context) -> when(mock.read(any())).thenReturn(handshake));
                MockedConstruction<ScaleCodecWriter> writerMock = mockConstruction(ScaleCodecWriter.class)
        ) {
            blockAnnounceEngine.receiveRequest(message, stream);

            verify(connectionManager).addBlockAnnounceStream(stream);
        }
    }

    @Test
    void receiveHandshakeRequestWhenNotConnectedShouldSendHandshakeBack() throws IOException {
        byte[] message = new  byte[BlockAnnounceEngine.HANDSHAKE_LENGTH];
        Arrays.fill(message, (byte) 1);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isBlockAnnounceConnected(peerId)).thenReturn(false);
        when(handshakeBuilder.getBlockAnnounceHandshake()).thenReturn(handshake);
        try (
                MockedConstruction<ScaleCodecReader> readerMock = mockConstruction(ScaleCodecReader.class);
                MockedConstruction<ScaleCodecWriter> writerMock = mockConstruction(ScaleCodecWriter.class)
        ) {
            blockAnnounceEngine.receiveRequest(message, stream);
            ScaleCodecWriter writer = writerMock.constructed().getFirst();

            verify(writer).write(any(BlockAnnounceHandshakeScaleWriter.class), eq(handshake));
            verify(stream).writeAndFlush(any());
        }
    }

    @Test
    void receiveHandshakeRequestWhenAlreadyConnectedShouldCloseStream() {
        byte[] message = new byte[BlockAnnounceEngine.HANDSHAKE_LENGTH];
        Arrays.fill(message, (byte) 1);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isBlockAnnounceConnected(peerId)).thenReturn(true);
        try (MockedConstruction<ScaleCodecReader> readerMock = mockConstruction(ScaleCodecReader.class)) {
            blockAnnounceEngine.receiveRequest(message, stream);

            verify(stream).close();
        }
    }

    @Test
    void receiveBlockAnnounceWhenConnectedShouldUpdatePeer() {
        byte[] message = new byte[] { 1, 2, 3 };
        BlockAnnounceMessage blockAnnounceMessage = mock(BlockAnnounceMessage.class);
        when(blockAnnounceMessage.getHeader()).thenReturn(mock(BlockHeader.class));
        when(verifier.verifyAuthorship(any())).thenReturn(true);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isBlockAnnounceConnected(peerId)).thenReturn(true);

        try (MockedConstruction<ScaleCodecReader> readerMock = mockConstruction(ScaleCodecReader.class,
                (mock, context) -> when(mock.read(any(BlockAnnounceMessageScaleReader.class)))
                        .thenReturn(blockAnnounceMessage))
        ) {
            blockAnnounceEngine.receiveRequest(message, stream);

            verify(connectionManager).updatePeer(peerId, blockAnnounceMessage);
        }
    }

    @Test
    void receiveBlockAnnounceWhenConnectedShouldSyncMessage() {
        byte[] message = new byte[] { 1, 2, 3 };
        BlockAnnounceMessage blockAnnounceMessage = mock(BlockAnnounceMessage.class);
        when(blockAnnounceMessage.getHeader()).thenReturn(mock(BlockHeader.class));
        when(verifier.verifyAuthorship(any())).thenReturn(true);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isBlockAnnounceConnected(peerId)).thenReturn(true);

        try (MockedConstruction<ScaleCodecReader> readerMock = mockConstruction(ScaleCodecReader.class,
                (mock, context) -> when(mock.read(any(BlockAnnounceMessageScaleReader.class)))
                        .thenReturn(blockAnnounceMessage))
        ) {
            blockAnnounceEngine.receiveRequest(message, stream);

            verify(warpSyncState).syncBlockAnnounce(blockAnnounceMessage);
        }
    }

    @Test
    void writeBlockAnnounceMessage() {
        byte[] message = {1, 2, 3, 4};

        blockAnnounceEngine.writeBlockAnnounceMessage(stream, peerId, message);

        verify(stream).writeAndFlush(message);
    }
}