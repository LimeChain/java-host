package com.limechain.network.protocol.blockannounce;

import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceHandshakeBuilder;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockAnnounceControllerTest {
    @InjectMocks
    private BlockAnnounceController blockAnnounceController;
    @Mock
    private Stream stream;
    @Mock
    private PeerId peerId;
    @Mock
    private BlockAnnounceEngine engine;
    @Mock
    private BlockAnnounceHandshakeBuilder blockAnnounceHandshakeBuilder;

    @BeforeEach
    void setup() {
        blockAnnounceController.engine = engine;
        engine.handshakeBuilder = blockAnnounceHandshakeBuilder;
    }

    @Test
    void sendHanshake() {
        when(stream.remotePeerId()).thenReturn(peerId);
        blockAnnounceController.sendHandshake();
        verify(engine).writeHandshakeToStream(stream, peerId);
    }

    @Test
    void sendBlockAnnounceMessage() {
        byte[] message = {1, 2, 3, 4};
        when(stream.remotePeerId()).thenReturn(peerId);
        blockAnnounceController.sendBlockAnnounceMessage(message);
        verify(engine).writeBlockAnnounceMessage(stream, peerId, message);
    }
}