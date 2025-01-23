package com.limechain.network.protocol.grandpa;

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
class GrandpaControllerTest {
    @InjectMocks
    private GrandpaController grandpaController;
    @Mock
    private Stream stream;
    @Mock
    private PeerId peerId;
    @Mock
    private GrandpaEngine engine;

    @BeforeEach
    void setup() {
        grandpaController.engine = engine;
    }

    @Test
    void sendHandshake() {
        when(stream.remotePeerId()).thenReturn(peerId);
        grandpaController.sendHandshake();
        verify(engine).writeHandshakeToStream(stream, peerId);
    }

    @Test
    void sendNeighbourMessage() {
        when(stream.remotePeerId()).thenReturn(peerId);
        grandpaController.sendNeighbourMessage();
        verify(engine).writeNeighbourMessage(stream, peerId);
    }

    @Test
    void sendCommitMessage() {
        byte[] encodedCommitMessage = {1, 0, 0, 0, 2, 0, 1, 1, 1, 1, 0, 0, 0, 1, 2, 0};
        grandpaController.sendCommitMessage(encodedCommitMessage);
        verify(engine).writeCommitMessage(stream, encodedCommitMessage);
    }
}