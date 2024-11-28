package com.limechain.network;

import com.limechain.network.kad.KademliaService;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.blockannounce.scale.BlockAnnounceMessageScaleWriter;
import com.limechain.network.protocol.transaction.scale.TransactionWriter;
import com.limechain.transaction.dto.Extrinsic;
import com.limechain.transaction.dto.ExtrinsicArray;
import com.limechain.utils.async.AsyncExecutor;
import com.limechain.utils.scale.ScaleUtils;
import io.libp2p.core.PeerId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
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
            //TODO: We might not need that
//            asyncExecutor.executeAndForget(() ->
//                    network.getTransactionsService().sendTransactionsMessage(network.getHost(), peerId, new byte[0]));
        });
    }

    public void sendBlockAnnounceMessageExcludingPeer(BlockAnnounceMessage message, PeerId excluding) {
        byte[] scaleMessage = ScaleUtils.Encode.encode(new BlockAnnounceMessageScaleWriter(), message);
        sendMessageToActivePeers(p -> {
                    if (p.equals(excluding)) {
                        return;
                    }
                    asyncExecutor.executeAndForget(() -> network.getBlockAnnounceService().sendBlockAnnounceMessage(
                            network.getHost(), p, scaleMessage));
                }
        );
    }

    public void sendTransactionMessageExcludingPeer(Extrinsic extrinsic, Set<PeerId> excludingList) {
        ExtrinsicArray extrinsicArray = new ExtrinsicArray(new Extrinsic[]{extrinsic});
        byte[] scaleMessage = ScaleUtils.Encode.encode(new TransactionWriter(), extrinsicArray);

        sendMessageToActivePeers(p -> {
            if (excludingList.contains(p)) {
                return;
            }
            asyncExecutor.executeAndForget(() -> network.getTransactionsService().sendTransactionsMessage(
                    network.getHost(), p, scaleMessage
            ));
        });
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
