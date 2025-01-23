package com.limechain.network.protocol.grandpa;

import com.limechain.network.ConnectionManager;
import com.limechain.network.dto.PeerInfo;
import com.limechain.network.dto.ProtocolStreams;
import io.libp2p.core.AddressBook;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrandpaServiceTest {
    @InjectMocks
    private GrandpaService grandpaService = new GrandpaService("pid");
    @Mock
    private PeerInfo peerInfo;
    @Mock
    private ProtocolStreams protocolStreams;
    @Mock
    private Stream stream;
    @Mock
    private Host host;
    @Mock
    private PeerId peerId;
    @Mock
    private ConnectionManager connectionManager;
    @Mock
    private Grandpa protocol;
    @Mock
    private AddressBook addressBook;
    @Mock
    private GrandpaController grandpaController;

    private final byte[] encodedCommitMessage
            = new byte[]{2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0};

    //setup method
    @BeforeEach
    public void setupEach() throws NoSuchFieldException, IllegalAccessException {
        setPrivateFieldOfSuperclass(grandpaService, "protocol", protocol);
    }

    @Test
    void sendNeighbourMessageWhenNotConnectionShouldSendHandshake() {
        when(connectionManager.getPeerInfo(peerId)).thenReturn(peerInfo);
        when(peerInfo.getGrandpaStreams()).thenReturn(protocolStreams);
        when(protocolStreams.getInitiator()).thenReturn(null);
        when(host.getAddressBook()).thenReturn(addressBook);
        when(protocol.dialPeer(host, peerId, addressBook)).thenReturn(grandpaController);

        grandpaService.sendNeighbourMessage(host, peerId);

        verify(grandpaController).sendHandshake();
    }

    @Test
    void sendNeighbourMessageWhenExistingConnection() {
        when(connectionManager.getPeerInfo(peerId)).thenReturn(peerInfo);
        when(peerInfo.getGrandpaStreams()).thenReturn(protocolStreams);
        when(protocolStreams.getInitiator()).thenReturn(stream);

        try (MockedConstruction<GrandpaController> mock = mockConstruction(GrandpaController.class)) {
            grandpaService.sendNeighbourMessage(host, peerId);

            assertEquals(1, mock.constructed().size());
            GrandpaController controller = mock.constructed().get(0);
            verify(controller).sendNeighbourMessage();
        }
    }

    @Test
    void sendCommitMessageWhenNotConnectionShouldSendHandshake() {
        when(connectionManager.getPeerInfo(peerId)).thenReturn(peerInfo);
        when(peerInfo.getGrandpaStreams()).thenReturn(protocolStreams);
        when(protocolStreams.getInitiator()).thenReturn(null);
        when(host.getAddressBook()).thenReturn(addressBook);
        when(protocol.dialPeer(host, peerId, addressBook)).thenReturn(grandpaController);

        grandpaService.sendCommitMessage(host, peerId, encodedCommitMessage);

        verify(grandpaController).sendHandshake();
    }

    @Test
    void sendCommitMessageWhenExistingConnection() {
        when(connectionManager.getPeerInfo(peerId)).thenReturn(peerInfo);
        when(peerInfo.getGrandpaStreams()).thenReturn(protocolStreams);
        when(protocolStreams.getInitiator()).thenReturn(stream);

        try (MockedConstruction<GrandpaController> mock = mockConstruction(GrandpaController.class)) {
            grandpaService.sendCommitMessage(host, peerId, encodedCommitMessage);

            assertEquals(1, mock.constructed().size());
            GrandpaController controller = mock.constructed().get(0);
            verify(controller).sendCommitMessage(encodedCommitMessage);
        }
    }

    // Setting private fields. Not a good idea in general.
    // Necessary due to mockito's newer versions not being able to inject generic type fields in superclass
    private void setPrivateFieldOfSuperclass(Object object, String fieldName, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        Field privateField = object.getClass().getSuperclass().getDeclaredField(fieldName);
        privateField.setAccessible(true);

        privateField.set(object, value);
    }
}