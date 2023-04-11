package com.limechain.network.substream.sync;

import com.google.protobuf.ByteString;
import com.limechain.network.kad.KademliaService;
import com.limechain.network.protocol.sync.SyncService;
import com.limechain.network.substream.sync.pb.SyncMessage;
import io.ipfs.multihash.Multihash;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.protocol.Ping;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.peergos.HostBuilder;

import java.math.BigInteger;
import java.util.List;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SyncTest {
    private Host senderNode = null;
    private KademliaService kademliaService = null;
    private SyncService syncService = null;

    @BeforeAll
    public void init() {
        HostBuilder hostBuilder = (new HostBuilder()).generateIdentity().listenLocalhost(10000 + new Random().nextInt(50000));

        syncService = new SyncService();
        kademliaService = new KademliaService("/dot/kad", Multihash.deserialize(hostBuilder.getPeerId().getBytes()), false);

        hostBuilder.addProtocols(List.of(new Ping(), kademliaService.getDht(), syncService.getSyncMessages()));
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

    //@Test
    public void remoteBlockRequest_returnCorrectBlock_ifGivenBlockHash() {
        var peerId = PeerId.fromBase58("12D3KooWHsvEicXjWWraktbZ4MQBizuyADQtuEGr3NbDvtm5rFA5");
        var receivers = new String[]{"/dns/p2p.0.polkadot.network/tcp/30333/p2p/12D3KooWHsvEicXjWWraktbZ4MQBizuyADQtuEGr3NbDvtm5rFA5"};

        int connectedNodes = kademliaService.connectBootNodes(receivers);
        int expectedConnectedNodes = 1;
        assertEquals(expectedConnectedNodes, connectedNodes);

        //CHECKSTYLE.OFF
        var response = syncService.getSyncMessages().remoteBlockRequest(senderNode, senderNode.getAddressBook(), peerId, 19, "cbd3e72e769652f804568a48889382edff4742074a7201309acfd1069e5de90a", null, null, SyncMessage.Direction.Ascending, 1);
        ByteString expected = ByteString.copyFrom(new byte[]{-53, -45, -25, 46, 118, -106, 82, -8, 4, 86, -118, 72, -120, -109, -126, -19, -1, 71, 66, 7, 74, 114, 1, 48, -102, -49, -47, 6, -98, 93, -23, 10});
        //CHECKSTYLE.ON
        assertNotNull(response);
        assertTrue(response.getBlocksCount() > 0);

        assertEquals(expected, response.getBlocks(0).getHash());
    }

    @Test
    public void remoteBlockRequest_returnCorrectBlock_ifGivenBlockNumber() {
        HostBuilder hostBuilder = (new HostBuilder()).generateIdentity().listenLocalhost(10000 + new Random().nextInt(50000));

        var syncService = new SyncService();
        var kademliaService = new KademliaService("/dot/kad", Multihash.deserialize(hostBuilder.getPeerId().getBytes()), false);

        hostBuilder.addProtocols(List.of(new Ping(), kademliaService.getDht(), syncService.getSyncMessages()));
        senderNode = hostBuilder.build();

        senderNode.start().join();
        System.out.println(senderNode.getPeerId());

        kademliaService.setHost(senderNode);
        var peerId = PeerId.fromBase58("12D3KooWHpQ5NB4ga1aLToVTDQ4XwCh8KbXpaTzdYEApo1s6GZQ1");
        var receivers = new String[]{"/ip4/127.0.0.1/tcp/30333/p2p/12D3KooWHpQ5NB4ga1aLToVTDQ4XwCh8KbXpaTzdYEApo1s6GZQ1"};

        int connectedNodes = kademliaService.connectBootNodes(receivers);
        int expectedConnectedNodes = 1;
        assertEquals(expectedConnectedNodes, connectedNodes);
        //CHECKSTYLE.OFF
        var response = syncService.getSyncMessages().remoteBlockRequest(senderNode, senderNode.getAddressBook(), peerId, 19, null,
                BigInteger.valueOf(3100).toByteArray(), BigInteger.valueOf(3100).toByteArray(), SyncMessage.Direction.Ascending, 1);
        ByteString expected = ByteString.copyFrom(new byte[]{-53, -45, -25, 46, 118, -106, 82, -8, 4, 86, -118, 72, -120, -109, -126, -19, -1, 71, 66, 7, 74, 114, 1, 48, -102, -49, -47, 6, -98, 93, -23, 10});
        //CHECKSTYLE.ON
        assertNotNull(response);
        assertTrue(response.getBlocksCount() > 0);

        assertEquals(expected, response.getBlocks(0).getHash());
        System.out.println(response.getBlocks(0));
    }
}