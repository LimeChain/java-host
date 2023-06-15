package com.limechain.network;

import com.limechain.chain.Chain;
import com.limechain.chain.ChainService;
import com.limechain.config.HostConfig;
import com.limechain.network.kad.RamAddressBook;
import com.limechain.network.kad.KademliaService;
import com.limechain.network.protocol.blockannounce.BlockAnnounceService;
import com.limechain.network.protocol.lightclient.LightMessagesService;
import com.limechain.network.protocol.sync.SyncService;
import com.limechain.network.protocol.warp.WarpSyncService;
import io.ipfs.multiaddr.MultiAddress;
import io.ipfs.multihash.Multihash;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.protocol.Ping;
import lombok.Getter;
import lombok.extern.java.Log;
import org.peergos.HostBuilder;
import org.peergos.PeerAddresses;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * A Singleton Network class that handles all peer connections and Kademlia
 */
@Component
@Log
public class Network {
    private static final int HOST_PORT = 30333;
    private static Network network;
    @Getter
    private final Host host;
    public SyncService syncService;
    public LightMessagesService lightMessagesService;
    public WarpSyncService warpSyncService;
    public KademliaService kademliaService;
    public BlockAnnounceService blockAnnounceService;

    private final RamAddressBook addressBook = new RamAddressBook();

    /**
     * Initializes a host for the peer connection,
     * Initializes the Kademlia service
     * Manages if nodes running locally are going to be allowed
     * Connects Kademlia to boot nodes
     *
     * @param chainService chain specification information containing boot nodes
     * @param hostConfig   host configuration containing current network
     */
    private Network(ChainService chainService, HostConfig hostConfig) {
        boolean isLocalEnabled = hostConfig.getChain() == Chain.LOCAL;
        boolean clientMode = true;

        HostBuilder hostBuilder = new HostBuilder().generateIdentity().listenLocalhost(HOST_PORT);
        Multihash hostId = Multihash.deserialize(hostBuilder.getPeerId().getBytes());

        //TODO: Add new protocolId format with genesis hash
        String chainId = chainService.getGenesis().getProtocolId();
        String legacyKadProtocolId = String.format("/%s/kad", chainId);
        String legacyWarpProtocolId = String.format("/%s/sync/warp", chainId);
        String legacyLightProtocolId = String.format("/%s/light/2", chainId);
        String legacySyncProtocolId = String.format("/%s/sync/2", chainId);
        String legacyBlockAnnounceProtocolId = String.format("/%s/block-announces/1", chainId);

        kademliaService = new KademliaService(legacyKadProtocolId, hostId, isLocalEnabled, clientMode);
        lightMessagesService = new LightMessagesService(legacyLightProtocolId);
        warpSyncService = new WarpSyncService(legacyWarpProtocolId);
        syncService = new SyncService(legacySyncProtocolId);
        blockAnnounceService = new BlockAnnounceService(legacyBlockAnnounceProtocolId);

        hostBuilder.addProtocols(
                List.of(
                        new Ping(),
                        kademliaService.getProtocol(),
                        lightMessagesService.getProtocol(),
                        warpSyncService.getProtocol(),
                        syncService.getProtocol(),
                        blockAnnounceService.getProtocol()
                )
        );

        host = hostBuilder.build();

        kademliaService.host = host;
        kademliaService.connectBootNodes(chainService.getGenesis().getBootNodes());
        kademliaService.getProtocol().setAddressBook(addressBook);
    }

    /**
     * @return Network class instance
     */
    public static Network getInstance() {
        if (network != null) {
            return network;
        }
        throw new AssertionError("Network not initialized.");
    }

    /**
     * Initializes singleton Network instance
     * This is used two times on startup
     *
     * @return Network instance saved in class or if not found returns new Network instance
     */
    public static Network initialize(ChainService chainService, HostConfig hostConfig) {
        if (network != null) {
            log.log(Level.WARNING, "Network module already initialized.");
            return network;
        }
        network = new Network(chainService, hostConfig);
        log.log(Level.INFO, "Initialized network module!");
        return network;
    }

    public void start() {
        // TODO: Connect to bootnodes, start new peers search cron job
    }

    public String getPeerId() {
        return this.host.getPeerId().toString();
    }

    public String[] getListenAddresses() {
        // TODO Bug: .listenAddresses() returns empty list
        return this.host.listenAddresses().stream().map(Multiaddr::toString).toArray(String[]::new);
    }

    public int getPeersCount() {
        return addressBook.getAddresses().size();
    }

    public List<PeerAddresses> getPeers() {
        return addressBook.getAddresses().entrySet().stream().map(this::mapAddressBookEntryToPeerAddresses).toList();
    }

    private PeerAddresses mapAddressBookEntryToPeerAddresses(Map.Entry<PeerId, Set<Multiaddr>> entry) {
        return new PeerAddresses(
                mapPeerIdToHash(entry.getKey()),
                mapMultaddrSetToMultiAddressList(entry.getValue())
        );
    }

    private Multihash mapPeerIdToHash(PeerId peerId) {
        return Multihash.deserialize(peerId.getBytes());
    }

    private List<MultiAddress> mapMultaddrSetToMultiAddressList(Set<Multiaddr> multiaddrSet) {
        return multiaddrSet.stream().map(multiaddr -> new MultiAddress(multiaddr.serialize())).toList();
    }

    /**
     * Periodically searches for new peers and connects to them
     * Logs the number of connected peers excluding boot nodes
     * By default Spring Boot uses a thread pool of size 1, so each call will be executed one at a time.
     */
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    public void findPeers() {
        log.log(Level.INFO, "Searching for peers...");
        kademliaService.findNewPeers();

        log.log(Level.INFO, String.format("Connected peers: %s", getPeersCount()));
    }
}
