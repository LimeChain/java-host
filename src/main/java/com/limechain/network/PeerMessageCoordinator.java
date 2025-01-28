package com.limechain.network;

import com.limechain.network.kad.KademliaService;
import com.limechain.network.protocol.blockannounce.NodeRole;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.blockannounce.scale.BlockAnnounceMessageScaleWriter;
import com.limechain.network.protocol.grandpa.messages.catchup.req.CatchUpReqMessage;
import com.limechain.network.protocol.grandpa.messages.catchup.req.CatchUpReqMessageScaleWriter;
import com.limechain.network.protocol.grandpa.messages.catchup.res.CatchUpResMessage;
import com.limechain.network.protocol.grandpa.messages.catchup.res.CatchUpResMessageScaleWriter;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessageScaleWriter;
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
    private final NetworkService network;

    public PeerMessageCoordinator(NetworkService network) {
        this.network = network;

        asyncExecutor = AsyncExecutor.withPoolSize(50);
    }

    public void handshakePeers() {
        sendMessageToActivePeers(peerId -> {
            asyncExecutor.executeAndForget(() ->
                    network.getGrandpaService().sendHandshake(network.getHost(), peerId));

            if (network.getNodeRole().equals(NodeRole.AUTHORING)) {
                asyncExecutor.executeAndForget(() ->
                        network.getTransactionsService().sendHandshake(network.getHost(), peerId));
            }
        });
    }

    @Scheduled(fixedRate = 5, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void sendMessagesToPeers() {
        sendMessageToActivePeers(peerId -> {
            asyncExecutor.executeAndForget(() ->
                    network.getGrandpaService().sendNeighbourMessage(network.getHost(), peerId));
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

    /**
     * Scale encodes the provided extrinsic and sends it to all connected peers, excluding
     * those specified in the provided set. The excluded peers are typically the ones that
     * originally sent the transaction to our node.
     *
     * @param extrinsic     the transaction data to encode and propagate to peers.
     * @param peersToIgnore a set of peer IDs that should not receive the transaction
     */
    public void sendTransactionMessageExcludingPeer(Extrinsic extrinsic, Set<PeerId> peersToIgnore) {
        ExtrinsicArray extrinsicArray = new ExtrinsicArray(new Extrinsic[]{extrinsic});
        byte[] scaleMessage = ScaleUtils.Encode.encode(new TransactionWriter(), extrinsicArray);

        sendMessageToActivePeers(p -> {
            if (peersToIgnore.contains(p)) {
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

    public void sendCommitMessageToPeers(CommitMessage commitMessage) {
        byte[] scaleMessage = ScaleUtils.Encode.encode(CommitMessageScaleWriter.getInstance(), commitMessage);
        sendMessageToActivePeers(peerId -> {
            asyncExecutor.executeAndForget(() -> network.getGrandpaService().sendCommitMessage(
                    network.getHost(), peerId, scaleMessage
            ));
        });
    }

    public void sendCatchUpRequestToPeer(PeerId peerId, CatchUpReqMessage catchUpReqMessage) {
        byte[] scaleMessage = ScaleUtils.Encode.encode(CatchUpReqMessageScaleWriter.getInstance(), catchUpReqMessage);
        network.getGrandpaService().sendCatchUpRequest(network.getHost(), peerId, scaleMessage);
    }

    public void sendCatchUpResponseToPeer(PeerId peerId, CatchUpResMessage catchUpResMessage) {
        byte[] scaleMessage = ScaleUtils.Encode.encode(CatchUpResMessageScaleWriter.getInstance(), catchUpResMessage);
        network.getGrandpaService().sendCatchUpResponse(network.getHost(), peerId, scaleMessage);
    }
}
