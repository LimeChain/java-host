package com.limechain.network.protocol.grandpa;

import com.limechain.network.ConnectionManager;
import com.limechain.network.protocol.NetworkService;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import lombok.extern.java.Log;

import java.util.Optional;

/**
 * Service for sending messages on {@link Grandpa} protocol.
 */
@Log
public class GrandpaService extends NetworkService<Grandpa> {
    ConnectionManager connectionManager = ConnectionManager.getInstance();

    public GrandpaService(String protocolId) {
        this.protocol = new Grandpa(protocolId, new GrandpaProtocol());
    }

    /**
     * Sends a neighbour message to a peer. If there is no initiator stream opened with the peer,
     * sends a handshake instead.
     *
     * @param us     our host object
     * @param peerId message receiver
     */
    public void sendNeighbourMessage(Host us, PeerId peerId) {
        //TODO Yordan: we should take care of handshakes separately.
        Optional.ofNullable(connectionManager.getPeerInfo(peerId))
                .map(p -> p.getGrandpaStreams().getInitiator())
                .ifPresentOrElse(
                        this::sendNeighbourMessage,
                        () -> sendHandshake(us, peerId)
                );
    }

    private void sendNeighbourMessage(Stream stream) {
        GrandpaController controller = new GrandpaController(stream);
        controller.sendNeighbourMessage();
    }

    public void sendHandshake(Host us, PeerId peerId) {
        try {
            GrandpaController controller = this.protocol.dialPeer(us, peerId, us.getAddressBook());
            controller.sendHandshake();
        } catch (Exception e) {
            log.warning("Failed to send Grandpa handshake to " + peerId);
        }
    }
}
