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

    /**
     * Sends a commit message to a peer. If there is no initiator stream opened with the peer,
     * sends a handshake instead.
     *
     * @param us                   our host object
     * @param peerId               message receiver
     * @param encodedCommitMessage scale encoded representation of the CommitMessage object
     */
    public void sendCommitMessage(Host us, PeerId peerId, byte[] encodedCommitMessage) {
        Optional.ofNullable(connectionManager.getPeerInfo(peerId))
                .map(p -> p.getGrandpaStreams().getInitiator())
                .ifPresentOrElse(
                        stream -> new GrandpaController(stream).sendCommitMessage(encodedCommitMessage),
                        () -> sendHandshake(us, peerId)
                );
    }

    /**
     * Sends a catch-up request message to a peer. If there is no initiator stream opened with the peer,
     * sends a handshake instead.
     *
     * @param us                       our host object
     * @param peerId                   message receiver
     * @param encodedCatchUpReqMessage scale encoded representation of the CatchUpReqMessage object
     */
    public void sendCatchUpRequest(Host us, PeerId peerId, byte[] encodedCatchUpReqMessage) {
        Optional.ofNullable(connectionManager.getPeerInfo(peerId))
                .map(p -> p.getGrandpaStreams().getInitiator())
                .ifPresentOrElse(
                        stream -> new GrandpaController(stream).sendCatchUpRequest(encodedCatchUpReqMessage),
                        () -> sendHandshake(us, peerId)
                );
    }

    /**
     * Sends a catch-up response message to a peer. If there is no initiator stream opened with the peer,
     * sends a handshake instead.
     *
     * @param us                       our host object
     * @param peerId                   message receiver
     * @param encodedCatchUpResMessage scale encoded representation of the CatchUpResMessage object
     */
    public void sendCatchUpResponse(Host us, PeerId peerId, byte[] encodedCatchUpResMessage) {
        Optional.ofNullable(connectionManager.getPeerInfo(peerId))
                .map(p -> p.getGrandpaStreams().getInitiator())
                .ifPresentOrElse(
                        stream -> new GrandpaController(stream).sendCatchUpResponse(encodedCatchUpResMessage),
                        () -> sendHandshake(us, peerId)
                );
    }

    /**
     * Sends a vote message to a peer. If there is no initiator stream opened with the peer,
     * sends a handshake instead.
     *
     * @param us                   our host object
     * @param peerId               message receiver
     * @param encodedVoteMessage scale encoded representation of the VoteMessage object
     */
    public void sendVoteMessage(Host us, PeerId peerId, byte[] encodedVoteMessage) {
        Optional.ofNullable(connectionManager.getPeerInfo(peerId))
                .map(p -> p.getGrandpaStreams().getInitiator())
                .ifPresentOrElse(
                        stream -> new GrandpaController(stream).sendVoteMessage(encodedVoteMessage),
                        () -> sendHandshake(us, peerId)
                );
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
