package com.limechain.network;

import com.limechain.network.kad.KademliaService;
import com.limechain.utils.async.AsyncExecutor;
import io.libp2p.core.PeerId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
public class PeerMessageCoordinator {

    private final AsyncExecutor asyncExecutor;
    private final Network network;

    public PeerMessageCoordinator(Network network) {
        this.network = network;

        asyncExecutor = AsyncExecutor.withPoolSize(50);
    }

    public void handshakePeers() {
        sendMessageToActivePeers(peerId -> {
            asyncExecutor.executeAndForget(() ->
                    network.getGrandpaService().sendHandshake(network.getHost(), peerId));
            asyncExecutor.executeAndForget(() ->
                    network.getTransactionsService().sendHandshake(network.getHost(), peerId));
        });
    }

    @Scheduled(fixedRate = 5, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void sendMessagesToPeers() {
        sendMessageToActivePeers(peerId -> {
            asyncExecutor.executeAndForget(() ->
                    network.getGrandpaService().sendNeighbourMessage(network.getHost(), peerId));
            asyncExecutor.executeAndForget(() ->
                    network.getTransactionsService().sendTransactionsMessage(network.getHost(), peerId));
        });
    }

    public void sendBlockAnnounceMessage(byte[] encodedBlockAnnounceMessage) {
        sendMessageToActivePeers(peerId ->
                asyncExecutor.executeAndForget(() -> network.getBlockAnnounceService().sendBlockAnnounceMessage(
                        network.getHost(), peerId, encodedBlockAnnounceMessage))
        );
    }

    private void sendMessageToActivePeers(Consumer<PeerId> messageAction) {
        network.getConnectionManager().getPeerIds().forEach(messageAction);
    }

    public void handshakeBootNodes() {
        KademliaService kademliaService = network.getKademliaService();
        kademliaService.getBootNodePeerIds()
                .stream()
                .distinct()
                .forEach(p -> asyncExecutor.executeAndForget(() ->
                        network.getBlockAnnounceService().sendHandshake(kademliaService.getHost(), p)));
    }

    public void sendNeighbourMessageToPeer(PeerId peerId) {
        network.getGrandpaService().sendNeighbourMessage(network.getHost(), peerId);
    }
}
