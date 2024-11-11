package com.limechain.network.protocol.blockannounce;

import com.limechain.network.protocol.NetworkService;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import lombok.extern.java.Log;

@Log
public class BlockAnnounceService extends NetworkService<BlockAnnounce> {
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
}
