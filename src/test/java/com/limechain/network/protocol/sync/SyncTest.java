package com.limechain.network.protocol.sync;

import com.google.protobuf.ByteString;
import com.limechain.network.kad.KademliaService;
import com.limechain.network.protocol.sync.pb.SyncMessage;
import com.limechain.utils.RandomGenerationUtils;
import io.emeraldpay.polkaj.types.Hash256;
import io.ipfs.multiaddr.MultiAddress;
import io.ipfs.multihash.Multihash;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.protocol.Ping;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.peergos.HostBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SyncTest {
    public static final String PEER_ID = "12D3KooWPyEvPEXghnMC67Gff6PuZiSvfx3fmziKiPZcGStZ5xff";
    public static final String PEER_URL = "/dns/dot14.rotko.net/tcp/33214/p2p/";
    private Host senderNode = null;
    private KademliaService kademliaService = null;
    private SyncMessages syncService = null;

    @BeforeAll
    public void init() {
        MultiAddress address = RandomGenerationUtils.generateRandomAddress();
        HostBuilder hostBuilder = new HostBuilder()
                .generateIdentity()
                .listen(List.of(address));

        syncService = new SyncMessages("/dot/sync/2", new SyncProtocol());
        kademliaService = new KademliaService("/dot/kad",
                Multihash.deserialize(hostBuilder.getPeerId().getBytes()), false, true);

        hostBuilder.addProtocols(List.of(new Ping(), kademliaService.getProtocol(), syncService));
        senderNode = hostBuilder.build();

        senderNode.start().join();

        kademliaService.setHost(senderNode);
    }

    @AfterAll
    public void stopNode() {
        if (senderNode != null) {
            senderNode.stop();
        }
    }

    @Test
    @Disabled("Flaky test, connection sometimes times out...")
    void remoteBlockRequest_returnCorrectBlock_ifGivenBlockHash() {
        var peerId = PeerId.fromBase58(PEER_ID);
        var receivers = new String[]{PEER_URL + PEER_ID};
        int connectedNodes = kademliaService.connectBootNodes(receivers);
        int expectedConnectedNodes = 1;
        assertEquals(expectedConnectedNodes, connectedNodes);

        var response = syncService.remoteBlockRequest(senderNode, peerId,
                new BlockRequestDto(17,
                        Hash256.from("cbd3e72e769652f804568a48889382edff4742074a7201309acfd1069e5de90a"),
                        null,
                        SyncMessage.Direction.Ascending,
                        1));
        ByteString expected = ByteString.copyFrom(
                new byte[]{-53, -45, -25, 46, 118, -106, 82, -8, 4, 86, -118, 72, -120, -109, -126, -19, -1, 71, 66, 7,
                        74, 114, 1, 48, -102, -49, -47, 6, -98, 93, -23, 10});
        assertNotNull(response);
        assertTrue(response.getBlocksCount() > 0);

        assertEquals(expected, response.getBlocks(0).getHash());
    }

    @Test
    @Disabled("Flaky test, connection sometimes times out...")
    void remoteBlockRequest_returnCorrectBlock_ifGivenBlockNumber() {
        var peerId = PeerId.fromBase58(PEER_ID);
        var receivers = new String[]{PEER_URL + PEER_ID};
        int connectedNodes = kademliaService.connectBootNodes(receivers);
        int expectedConnectedNodes = 1;
        assertEquals(expectedConnectedNodes, connectedNodes);
        var response = syncService.remoteBlockRequest(senderNode, peerId,
                new BlockRequestDto(19, null,
                        15_030_299, SyncMessage.Direction.Ascending, 1));
        ByteString expected = ByteString.copyFrom(
                new byte[]{-53, -45, -25, 46, 118, -106, 82, -8, 4, 86, -118, 72, -120, -109, -126, -19, -1, 71, 66, 7,
                        74, 114, 1, 48, -102, -49, -47, 6, -98, 93, -23, 10});
        assertNotNull(response);
        assertTrue(response.getBlocksCount() > 0);

        assertEquals(expected, response.getBlocks(0).getHash());
    }

}
