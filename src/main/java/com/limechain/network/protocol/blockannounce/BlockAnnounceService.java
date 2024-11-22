package com.limechain.network.protocol.blockannounce;

import com.limechain.network.ConnectionManager;
import com.limechain.network.protocol.NetworkService;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import lombok.extern.java.Log;

import java.util.Optional;

@Log
public class BlockAnnounceService extends NetworkService<BlockAnnounce> {
    ConnectionManager connectionManager = ConnectionManager.getInstance();

    public BlockAnnounceService(String protocolId) {
        this.protocol = new BlockAnnounce(protocolId, new BlockAnnounceProtocol());
    }

    public void sendHandshake(Host us, PeerId peer) {
        try {
            BlockAnnounceController controller = this.protocol.dialPeer(us, peer, us.getAddressBook());
            controller.sendHandshake();
        } catch (IllegalStateException e) {
            log.warning("Error sending handshake request to peer " + peer);
        }
    }

    /**
     * Sends a Block Announce message to a peer. If there is no initiator stream opened with the peer,
     * sends a handshake instead.
     *
     * @param us     our host object
     * @param peerId message receiver
     */
    public void sendBlockAnnounceMessage(Host us, PeerId peerId, byte[] encodedBlockAnnounceMessage) {
        Optional.ofNullable(connectionManager.getPeerInfo(peerId))
                .map(p -> p.getBlockAnnounceStreams().getInitiator())
                .ifPresentOrElse(
                        stream -> new BlockAnnounceController(stream).sendBlockAnnounceMessage(encodedBlockAnnounceMessage),
                        () -> sendHandshake(us, peerId)
                );
    }
}
