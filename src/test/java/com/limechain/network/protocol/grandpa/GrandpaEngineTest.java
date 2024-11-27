package com.limechain.network.protocol.grandpa;

import com.limechain.network.ConnectionManager;
import com.limechain.network.dto.PeerInfo;
import com.limechain.network.protocol.blockannounce.NodeRole;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceHandshake;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceHandshakeBuilder;
import com.limechain.network.protocol.grandpa.messages.catchup.req.CatchUpReqMessage;
import com.limechain.network.protocol.grandpa.messages.catchup.req.CatchUpReqMessageScaleReader;
import com.limechain.network.protocol.grandpa.messages.catchup.res.CatchUpMessage;
import com.limechain.network.protocol.grandpa.messages.catchup.res.CatchUpMessageScaleReader;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessageScaleReader;
import com.limechain.network.protocol.grandpa.messages.neighbour.NeighbourMessage;
import com.limechain.network.protocol.grandpa.messages.neighbour.NeighbourMessageScaleReader;
import com.limechain.network.protocol.grandpa.messages.vote.VoteMessage;
import com.limechain.network.protocol.grandpa.messages.vote.VoteMessageScaleReader;
import com.limechain.network.protocol.message.ProtocolMessageBuilder;
import com.limechain.sync.warpsync.WarpSyncState;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unused")
@ExtendWith(MockitoExtension.class)
class GrandpaEngineTest {
    @InjectMocks
    private GrandpaEngine grandpaEngine;
    @Mock
    private Stream stream;
    @Mock
    private PeerId peerId;
    @Mock
    private ConnectionManager connectionManager;
    @Mock
    private WarpSyncState warpSyncState;
    @Mock
    private ProtocolMessageBuilder protocolMessageBuilder;
    @Mock
    private BlockAnnounceHandshakeBuilder blockAnnounceHandshakeBuilder;

    private final NeighbourMessage neighbourMessage =
            new NeighbourMessage(1, BigInteger.ONE, BigInteger.TWO, BigInteger.TEN);
    private final byte[] encodedNeighbourMessage
            = new byte[]{2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0};

    @Test
    void receiveRequestWithUnknownGrandpaTypeShouldLogAndIgnore() {
        byte[] unknownTypeMessage = new byte[]{7, 1, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0};

        grandpaEngine.receiveRequest(unknownTypeMessage, stream);

        verifyNoInteractions(connectionManager);
        verifyNoInteractions(warpSyncState);
    }

    // INITIATOR STREAM
    @Test
    void receiveNonHandshakeRequestOnInitiatorStreamShouldLogAndIgnore() {
        byte[] message = new byte[]{2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0};
        when(stream.isInitiator()).thenReturn(true);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(peerId.toString()).thenReturn("P1");

        grandpaEngine.receiveRequest(message, stream);

        verifyNoInteractions(connectionManager);
        verifyNoInteractions(warpSyncState);
    }

    @Test
    void receiveHandshakeOnInitiatorStreamShouldAddStreamToConnection() {
        byte[] message = new byte[]{2};
        when(stream.isInitiator()).thenReturn(true);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(protocolMessageBuilder.buildNeighbourMessage()).thenReturn(neighbourMessage);

        grandpaEngine.receiveRequest(message, stream);

        verify(connectionManager).addGrandpaStream(stream);
    }

    @Test
    void receiveHandshakeOnInitiatorStreamShouldSendNeighbourMessageBack() {
        byte[] message = new byte[]{2};
        when(protocolMessageBuilder.buildNeighbourMessage()).thenReturn(neighbourMessage);
        when(stream.isInitiator()).thenReturn(true);

        grandpaEngine.receiveRequest(message, stream);

        verify(stream).writeAndFlush(encodedNeighbourMessage);
    }

    // RESPONDER STREAM
    @Test
    void receiveNonHandshakeRequestOnResponderStreamWhenNotConnectedShouldLogAndCloseStream() {
        byte[] message = new byte[]{2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0};
        when(stream.isInitiator()).thenReturn(false);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isGrandpaConnected(peerId)).thenReturn(false);

        grandpaEngine.receiveRequest(message, stream);

        verifyNoMoreInteractions(connectionManager);
        verifyNoInteractions(warpSyncState);
        verify(stream).close();
    }

    @Test
    void receiveHandshakeRequestOnResponderStreamWhenAlreadyConnectedShouldLogAndCloseStream() {
        byte[] message = new byte[]{2};
        when(stream.isInitiator()).thenReturn(false);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isGrandpaConnected(peerId)).thenReturn(true);

        grandpaEngine.receiveRequest(message, stream);

        verifyNoMoreInteractions(connectionManager);
        verifyNoInteractions(warpSyncState);
        verify(stream).close();
    }

    @Test
    void receiveHandshakeRequestOnResponderStreamWhenNotConnectedShouldAddStreamToConnection() {
        byte[] message = new byte[]{2};
        when(stream.isInitiator()).thenReturn(false);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isGrandpaConnected(peerId)).thenReturn(false);
        when(connectionManager.getPeerInfo(peerId)).thenReturn(mock(PeerInfo.class));
        when(blockAnnounceHandshakeBuilder.getBlockAnnounceHandshake()).thenReturn(mock(BlockAnnounceHandshake.class));

        grandpaEngine.receiveRequest(message, stream);

        verify(connectionManager).addGrandpaStream(stream);
    }

    @Test
    void receiveHandshakeRequestOnResponderStreamWhenNotConnectedShouldSendHandshakeBack() {
        byte[] message = new byte[]{2};
        Integer role = NodeRole.LIGHT.getValue();

        when(stream.isInitiator()).thenReturn(false);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isGrandpaConnected(peerId)).thenReturn(false);
        when(connectionManager.getPeerInfo(peerId)).thenReturn(mock(PeerInfo.class));
        BlockAnnounceHandshake handshake = mock(BlockAnnounceHandshake.class);
        when(blockAnnounceHandshakeBuilder.getBlockAnnounceHandshake()).thenReturn(handshake);
        when(handshake.getNodeRole()).thenReturn(role);

        grandpaEngine.receiveRequest(message, stream);

        verify(stream).writeAndFlush(new byte[]{role.byteValue()});
    }

    @Test
    void receiveCommitMessageOnResponderStreamWhenShouldSyncCommit() {
        byte[] message = new byte[]{1, 2, 3};
        CommitMessage commitMessage = mock(CommitMessage.class);

        when(stream.isInitiator()).thenReturn(false);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isGrandpaConnected(peerId)).thenReturn(true);

        try (MockedConstruction<ScaleCodecReader> readerMock = mockConstruction(ScaleCodecReader.class,
                (mock, context) -> when(mock.read(any(CommitMessageScaleReader.class))).thenReturn(commitMessage))
        ) {
            grandpaEngine.receiveRequest(message, stream);

            verify(warpSyncState).syncCommit(commitMessage, peerId);
        }
    }

    @Test
    @Disabled("Unknown race condition causes some of the runs to fail")
        // TODO: find and fix the problem condition. Used to have a thread sleep for 100 millis before last verify
    void receiveNeighbourMessageOnResponderStreamWhenShouldSyncNeighbourMessage() {
        byte[] message = new byte[]{2, 1, -24, 60, 0, 0, 0, 0, 0, 0, 37, 6, 0, 0, 0, 0, 0, 0, -37, 118, 4, 1};
        NeighbourMessage neighbourMessage = mock(NeighbourMessage.class);

        when(stream.isInitiator()).thenReturn(false);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isGrandpaConnected(peerId)).thenReturn(true);
        try (MockedConstruction<ScaleCodecReader> readerMock = mockConstruction(ScaleCodecReader.class,
                (mock, context) -> when(mock.read(any(NeighbourMessageScaleReader.class))).thenReturn(neighbourMessage))
        ) {
            grandpaEngine.receiveRequest(message, stream);
            verify(warpSyncState).syncNeighbourMessage(neighbourMessage, peerId);
        }
    }

    @Test
    void receiveVoteMessageOnResponderStreamShouldDecodeLogAndIgnore() {
        byte[] message = new byte[]{0, 2, 3};
        VoteMessage voteMessage = mock(VoteMessage.class);

        when(stream.isInitiator()).thenReturn(false);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isGrandpaConnected(peerId)).thenReturn(true);

        try (MockedConstruction<ScaleCodecReader> readerMock = mockConstruction(ScaleCodecReader.class,
                (mock, context) -> when(mock.read(any(VoteMessageScaleReader.class))).thenReturn(voteMessage))
        ) {
            grandpaEngine.receiveRequest(message, stream);

            verifyNoMoreInteractions(connectionManager);
            verifyNoInteractions(warpSyncState);
        }
    }

    @Test
    void receiveCatchUpRequestMessageOnResponderStreamShouldLogAndIgnore() {
        byte[] message = new byte[]{3, 2, 3};
        CatchUpReqMessage catchUpReqMessage = mock(CatchUpReqMessage.class);

        when(stream.isInitiator()).thenReturn(false);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isGrandpaConnected(peerId)).thenReturn(true);

        try (MockedConstruction<ScaleCodecReader> readerMock = mockConstruction(ScaleCodecReader.class, (mock, context)
                -> when(mock.read(any(CatchUpReqMessageScaleReader.class))).thenReturn(catchUpReqMessage))
        ) {
            grandpaEngine.receiveRequest(message, stream);

            verifyNoMoreInteractions(connectionManager);
            verifyNoInteractions(warpSyncState);
        }
    }

    @Test
    void receiveCatchUpResponseMessageOnResponderStreamShouldLogAndIgnore() {
        byte[] message = new byte[]{4, 2, 3};
        CatchUpMessage catchUpMessage = mock(CatchUpMessage.class);

        when(stream.isInitiator()).thenReturn(false);
        when(stream.remotePeerId()).thenReturn(peerId);
        when(connectionManager.isGrandpaConnected(peerId)).thenReturn(true);

        try (MockedConstruction<ScaleCodecReader> readerMock = mockConstruction(ScaleCodecReader.class, (mock, context)
                -> when(mock.read(any(CatchUpMessageScaleReader.class))).thenReturn(catchUpMessage))
        ) {
            grandpaEngine.receiveRequest(message, stream);

            verifyNoMoreInteractions(connectionManager);
            verifyNoInteractions(warpSyncState);
        }
    }

    // WRITE
    @Test
    void writeHandshakeToStream() {
        Integer role = NodeRole.LIGHT.getValue();
        BlockAnnounceHandshake handshake = mock(BlockAnnounceHandshake.class);
        when(blockAnnounceHandshakeBuilder.getBlockAnnounceHandshake()).thenReturn(handshake);
        when(handshake.getNodeRole()).thenReturn(role);

        grandpaEngine.writeHandshakeToStream(stream, peerId);

        verify(stream).writeAndFlush(new byte[]{role.byteValue()});
    }

    @Test
    void writeNeighbourMessage() {
        when(protocolMessageBuilder.buildNeighbourMessage()).thenReturn(neighbourMessage);

        grandpaEngine.writeNeighbourMessage(stream, peerId);

        verify(stream).writeAndFlush(encodedNeighbourMessage);
    }
}