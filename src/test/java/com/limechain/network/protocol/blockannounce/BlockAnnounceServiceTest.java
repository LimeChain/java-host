package com.limechain.network.protocol.blockannounce;

import com.limechain.network.kad.KademliaService;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceHandshake;
import com.limechain.utils.RandomGenerationUtils;
import io.emeraldpay.polkaj.types.Hash256;
import io.ipfs.multiaddr.MultiAddress;
import io.ipfs.multihash.Multihash;
import io.libp2p.core.AddressBook;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.protocol.Ping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peergos.HostBuilder;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockAnnounceServiceTest {
    @Mock
    private BlockAnnounce protocol;
    @Mock
    private Host host;
    @Mock
    private PeerId peerId;
    @Mock
    private AddressBook addressBook;
    @Mock
    private BlockAnnounceController blockAnnounceController;

    private final BlockAnnounceService blockAnnounceService = new BlockAnnounceService("pid");

    @BeforeEach
    public void setupEach() throws NoSuchFieldException, IllegalAccessException {
        when(host.getAddressBook()).thenReturn(addressBook);
        setPrivateFieldOfSuperclass(blockAnnounceService, "protocol", protocol);
    }

    @Test
    void sendHandshake() {
        when(protocol.dialPeer(host, peerId, addressBook)).thenReturn(blockAnnounceController);

        blockAnnounceService.sendHandshake(host, peerId);

        verify(blockAnnounceController).sendHandshake();
    }

    @Disabled("This is an integration test")
    @Test
    void receivesNotifications() {
        Host senderNode = null;
        try {
            MultiAddress multiAddress = RandomGenerationUtils.generateRandomAddress();
            HostBuilder hostBuilder1 = new HostBuilder()
                    .generateIdentity()
                    .listen(List.of(multiAddress));

            var blockAnnounceService = new BlockAnnounceService("/dot/block-announces/1");
            var blockAnnounce = blockAnnounceService.getProtocol();
            var kademliaService = new KademliaService("/dot/kad",
                    Multihash.deserialize(hostBuilder1.getPeerId().getBytes()), true, false);

            hostBuilder1.addProtocols(List.of(new Ping(), blockAnnounce, kademliaService.getProtocol()));
            senderNode = hostBuilder1.build();

            senderNode.start().join();

            kademliaService.setHost(senderNode);

            //Polkadot
            var peerId = PeerId.fromBase58("12D3KooWPGSssFbR4XvuSfvu7Rdq4MUv82HdsygXZ4nRhEw3vJpC");

            var receivers = new String[]{
                    "/ip4/127.0.0.1/tcp/30333/p2p/" + peerId.toBase58()
            };

            kademliaService.connectBootNodes(receivers);

            var handshake = new BlockAnnounceHandshake() {{
                setNodeRole(4);
                setBestBlockHash(Hash256.from("0x7b22fc4469863c9671686c189a3238708033d364a77ba8d83e78777e7563f346"));
                setBestBlock(BigInteger.ZERO);
                setGenesisBlockHash(Hash256.from(
                        "0x7b22fc4469863c9671686c189a3238708033d364a77ba8d83e78777e7563f346"));
            }};

            Multiaddr[] addr = senderNode.getAddressBook().get(peerId)
                    .join().stream()
                    .filter(address -> !address.toString().contains("/ws") && !address.toString().contains("/wss"))
                    .toList()
                    .toArray(new Multiaddr[0]);

            if (addr.length == 0)
                throw new IllegalStateException("No addresses known for peer " + peerId);

            blockAnnounceService.sendHandshake(senderNode, peerId);

            Thread.sleep(60000);
        } catch (
                InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (senderNode != null) {
                senderNode.stop();
            }
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