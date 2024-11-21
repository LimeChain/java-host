package com.limechain.network;

import com.limechain.chain.Chain;
import com.limechain.chain.ChainService;
import com.limechain.cli.CliArguments;
import com.limechain.config.HostConfig;
import com.limechain.constants.GenesisBlockHash;
import com.limechain.network.kad.KademliaService;
import com.limechain.network.protocol.blockannounce.BlockAnnounceService;
import com.limechain.network.protocol.blockannounce.NodeRole;
import com.limechain.network.protocol.grandpa.GrandpaService;
import com.limechain.network.protocol.lightclient.LightMessagesService;
import com.limechain.network.protocol.ping.Ping;
import com.limechain.network.protocol.state.StateService;
import com.limechain.network.protocol.sync.SyncService;
import com.limechain.network.protocol.transaction.TransactionsService;
import com.limechain.network.protocol.warp.WarpSyncService;
import com.limechain.storage.DBConstants;
import com.limechain.storage.KVRepository;
import com.limechain.utils.Ed25519Utils;
import com.limechain.utils.StringUtils;
import com.limechain.utils.async.AsyncExecutor;
import io.ipfs.multiaddr.MultiAddress;
import io.ipfs.multihash.Multihash;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.crypto.keys.Ed25519PrivateKey;
import io.libp2p.protocol.PingProtocol;
import lombok.Getter;
import lombok.extern.java.Log;
import org.peergos.HostBuilder;
import org.peergos.protocol.IdentifyBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static com.limechain.network.kad.KademliaService.REPLICATION;

/**
 * A Network class that handles all peer connections and Kademlia
 */
@Component
@Log
public class Network {
    public static final String LOCAL_IPV4_TCP_ADDRESS = "/ip4/127.0.0.1/tcp/";
    private static final int HOST_PORT = 30333;
    private static final int ASYNC_EXECUTOR_POOL_SIZE = 10;
    private static final Random RANDOM = new Random();
    @Getter
    private final Chain chain;
    @Getter
    private final NodeRole nodeRole;
    private final String[] bootNodes;
    private final ConnectionManager connectionManager;
    @Getter
    private SyncService syncService;
    @Getter
    private StateService stateService;
    @Getter
    private WarpSyncService warpSyncService;
    @Getter
    private LightMessagesService lightMessagesService;
    @Getter
    private KademliaService kademliaService;
    private BlockAnnounceService blockAnnounceService;
    private GrandpaService grandpaService;
    private TransactionsService transactionsService;
    private Ping ping;
    @Getter
    private PeerId currentSelectedPeer;
    @Getter
    private Host host;
    private boolean started = false;
    private int bootPeerIndex = 0;

    /**
     * Initializes a host for the peer connection,
     * Initializes the Kademlia service
     * Manages if nodes running locally are going to be allowed
     * Connects Kademlia to boot nodes
     *
     * @param chainService     chain specification information containing boot nodes
     * @param hostConfig       host configuration containing current network
     * @param repository       database repository
     * @param cliArgs          command line arguments
     * @param genesisBlockHash genesis block hash
     */
    public Network(ChainService chainService, HostConfig hostConfig, KVRepository<String, Object> repository,
                   CliArguments cliArgs, GenesisBlockHash genesisBlockHash) {
        this.bootNodes = chainService.getChainSpec().getBootNodes();
        this.chain = hostConfig.getChain();
        this.nodeRole = hostConfig.getNodeRole();
        this.connectionManager = ConnectionManager.getInstance();
        this.initializeProtocols(chainService, genesisBlockHash, hostConfig, repository, cliArgs);
    }

    private void initializeProtocols(ChainService chainService, GenesisBlockHash genesisBlockHash,
                                     HostConfig hostConfig,
                                     KVRepository<String, Object> repository, CliArguments cliArgs) {
        boolean isLocalEnabled = hostConfig.getChain() == Chain.LOCAL;
        boolean clientMode = true;

        HostBuilder hostBuilder = new HostBuilder()
                .listen(List.of(new MultiAddress(LOCAL_IPV4_TCP_ADDRESS + HOST_PORT)));

        // The peerId is generated from the privateKey of the node
        hostBuilder.setPrivKey(loadPrivateKeyFromDB(repository, cliArgs));
        log.info("Current peerId " + hostBuilder.getPeerId().toString());
        Multihash hostId = Multihash.deserialize(hostBuilder.getPeerId().getBytes());

        String pingProtocol = ProtocolUtils.PING_PROTOCOL;
        String chainId = chainService.getChainSpec().getProtocolId();
        boolean legacyProtocol = !cliArgs.noLegacyProtocols();
        String protocolId = legacyProtocol ? chainId :
                StringUtils.remove0xPrefix(genesisBlockHash.getGenesisHash().toString());
        String kadProtocolId = ProtocolUtils.getKadProtocol(chainId);
        String warpProtocolId = ProtocolUtils.getWarpSyncProtocol(protocolId);
        String lightProtocolId = ProtocolUtils.getLightMessageProtocol(protocolId);
        String syncProtocolId = ProtocolUtils.getSyncProtocol(protocolId);
        String stateProtocolId = ProtocolUtils.getStateProtocol(protocolId);
        String blockAnnounceProtocolId = ProtocolUtils.getBlockAnnounceProtocol(protocolId);
        String grandpaProtocolId = ProtocolUtils.getGrandpaProtocol(protocolId, legacyProtocol);
        String transactionsProtocolId = ProtocolUtils.getTransactionsProtocol(protocolId);

        kademliaService = new KademliaService(kadProtocolId, hostId, isLocalEnabled, clientMode);
        lightMessagesService = new LightMessagesService(lightProtocolId);
        warpSyncService = new WarpSyncService(warpProtocolId);
        syncService = new SyncService(syncProtocolId);
        stateService = new StateService(stateProtocolId);
        blockAnnounceService = new BlockAnnounceService(blockAnnounceProtocolId);
        grandpaService = new GrandpaService(grandpaProtocolId);
        ping = new Ping(pingProtocol, new PingProtocol());
        transactionsService = new TransactionsService(transactionsProtocolId);

        hostBuilder.addProtocols(
                List.of(
                        ping,
                        kademliaService.getProtocol(),
                        lightMessagesService.getProtocol(),
                        warpSyncService.getProtocol(),
                        syncService.getProtocol(),
                        stateService.getProtocol(),
                        blockAnnounceService.getProtocol(),
                        grandpaService.getProtocol()
                )
        );

        if (nodeRole == NodeRole.AUTHORING) {
            hostBuilder.addProtocols(
                    List.of(
                            transactionsService.getProtocol()
                    )
            );
        }

        this.host = hostBuilder.build();
        IdentifyBuilder.addIdentifyProtocol(this.host);
        kademliaService.setHost(host);
    }

    private Ed25519PrivateKey loadPrivateKeyFromDB(KVRepository<String, Object> repository, CliArguments cliArgs) {
        Ed25519PrivateKey privateKey;

        if (cliArgs.nodeKey() != null && !cliArgs.nodeKey().isBlank()) {
            try {
                privateKey = Ed25519Utils.loadPrivateKey(StringUtils.hexToBytes(cliArgs.nodeKey()));
                log.log(Level.INFO, "PeerId loaded from arguments!");
                return privateKey;
            } catch (IllegalArgumentException ex) {
                log.severe("Provided secret key hex is invalid!");
            }
        }

        Optional<Object> peerIdKeyBytes = repository.find(DBConstants.PEER_ID);
        if (peerIdKeyBytes.isPresent()) {
            privateKey = Ed25519Utils.loadPrivateKey((byte[]) peerIdKeyBytes.get());
            log.log(Level.INFO, "PeerId loaded from database!");
        } else {
            privateKey = Ed25519Utils.generateKeyPair();
            repository.save(DBConstants.PEER_ID, privateKey.raw());
            log.log(Level.INFO, "Generated new peerId!");
        }
        return privateKey;
    }

    public void start() {
        log.log(Level.INFO, "Starting network module...");
        kademliaService.connectBootNodes(this.bootNodes);
        started = true;
        log.log(Level.INFO, "Started network module!");
    }

    public void stop() {
        log.log(Level.INFO, "Stopping network module...");
        started = false;
        connectionManager.removeAllPeers();
        host.stop();
        log.log(Level.INFO, "Stopped network module!");
    }

    public boolean updateCurrentSelectedPeerWithNextBootnode() {
        if (bootPeerIndex > kademliaService.getBootNodePeerIds().size())
            return false;
        this.currentSelectedPeer = this.kademliaService.getBootNodePeerIds().get(bootPeerIndex);
        bootPeerIndex++;
        return true;
    }

    public boolean updateCurrentSelectedPeerWithBootnode(int index) {
        if (index >= 0 && index < this.kademliaService.getBootNodePeerIds().size()) {
            this.currentSelectedPeer = this.kademliaService.getBootNodePeerIds().get(index);
            return true;
        }
        return false;
    }

    public void updateCurrentSelectedPeer() {
        if (connectionManager.getPeerIds().isEmpty()) return;
        this.currentSelectedPeer = connectionManager.getPeerIds().stream()
                .skip(RANDOM.nextInt(connectionManager.getPeerIds().size())).findAny().orElse(null);
    }

    public String getPeerId() {
        return this.host.getPeerId().toString();
    }

    public String[] getListenAddresses() {
        // TODO Bug: .listenAddresses() returns empty list
        return this.host.listenAddresses().stream().map(Multiaddr::toString).toArray(String[]::new);
    }

    public int getPeersCount() {
        return connectionManager.getPeerIds().size();
    }

    /**
     * Periodically searches for new peers and connects to them
     * Logs the number of connected peers excluding boot nodes
     * By default Spring Boot uses a thread pool of size 1, so each call will be executed one at a time.
     */
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    public void findPeers() {
        if (!started) {
            return;
        }
        if (getPeersCount() >= REPLICATION) {
            log.log(Level.INFO,
                    "Connections have reached replication factor(" + REPLICATION + "). " +
                            "No need to search for new ones yet.");
            return;
        }

        log.log(Level.INFO, "Searching for peers...");
        kademliaService.findNewPeers();

        if (this.currentSelectedPeer == null) {
            updateCurrentSelectedPeer();
        }

        log.log(Level.INFO, String.format("Connected peers: %s", getPeersCount()));
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void pingPeers() {
        // TODO: This needs to by synchronized with the findPeers method
        if (getPeersCount() == 0) {
            log.log(Level.INFO, "No peers to ping.");
            return;
        }

        log.log(Level.INFO, "Pinging peers...");
        connectionManager.getPeerIds().forEach(this::ping);
    }

    private void ping(PeerId peerId) {
        try {
            Long latency = ping.ping(host, host.getAddressBook(), peerId);
            log.log(Level.INFO, String.format("Pinged peer: %s, latency %s ms", peerId, latency));
        } catch (Exception e) {
            log.log(Level.WARNING, String.format("Failed to ping peer: %s. Removing from active connections", peerId));
            if (this.currentSelectedPeer.equals(peerId)) {
                updateCurrentSelectedPeer();
            }
        }
    }

    public void blockAnnounceHandshakeBootNodes() {
        kademliaService.getBootNodePeerIds()
                .stream()
                .distinct()
                .forEach(p -> new Thread(() -> blockAnnounceService.sendHandshake(this.host, p)).start());
    }

    public void handshakePeers() {
        connectionManager.getPeerIds().forEach(p -> {
            new Thread(() -> grandpaService.sendHandshake(this.host, p)).start();
            new Thread(() -> transactionsService.sendHandshake(this.host, p)).start();
        });
    }

    @Scheduled(fixedRate = 5, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void sendMessagesToPeers() {
        connectionManager.getPeerIds().forEach(peerId ->
                grandpaService.sendNeighbourMessage(this.host, peerId));
        connectionManager.getPeerIds().forEach(peerId ->
                transactionsService.sendTransactionsMessage(this.host, peerId));
    }

    public void sendNeighbourMessage(PeerId peerId) {
        grandpaService.sendNeighbourMessage(this.host, peerId);
    }

    public void sendBlockAnnounceMessage(byte[] encodedBlockAnnounceMessage) {
        AsyncExecutor asyncExecutor = AsyncExecutor.withPoolSize(ASYNC_EXECUTOR_POOL_SIZE);
        connectionManager.getPeerIds().forEach(p -> asyncExecutor.executeAndForget(() -> blockAnnounceService.sendBlockAnnounceMessage(this.host, p, encodedBlockAnnounceMessage)));
    }
}
