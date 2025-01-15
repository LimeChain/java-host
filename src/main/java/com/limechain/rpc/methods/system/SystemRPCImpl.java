package com.limechain.rpc.methods.system;

import com.limechain.chain.ChainService;
import com.limechain.chain.spec.ChainType;
import com.limechain.chain.spec.PropertyValue;
import com.limechain.config.SystemInfo;
import com.limechain.exception.global.ExecutionFailedException;
import com.limechain.exception.global.ThreadInterruptedException;
import com.limechain.exception.rpc.PeerNotFoundException;
import com.limechain.network.ConnectionManager;
import com.limechain.network.NetworkService;
import com.limechain.network.dto.PeerInfo;
import com.limechain.storage.block.state.BlockState;
import com.limechain.sync.state.SyncState;
import com.limechain.sync.warpsync.WarpSyncMachine;
import io.libp2p.core.PeerId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static java.util.Map.entry;

/**
 * Holds all business logic related to executing system rpc method calls.
 * <p>
 * It should implement {@link SystemRPC}, however due to jsonrpc4j limitations
 * described in {@link com.limechain.rpc.methods.RPCMethodsImpl} it doesn't do that
 */
@Service
@RequiredArgsConstructor
public class SystemRPCImpl {
    /**
     * Reference to services used when executing business logic
     */
    private final ChainService chainService;
    private final SystemInfo systemInfo;
    private final NetworkService network;
    private final WarpSyncMachine warpSync;
    private final SyncState syncState;
    private final BlockState blockState;
    private final ConnectionManager connectionManager = ConnectionManager.getInstance();

    /**
     * Gets the node's implementation name.
     */
    public String systemName() {
        return this.systemInfo.getHostName();
    }

    /**
     * Get the node implementation's version. Should be a semantic versioning string.
     */
    public String systemVersion() {
        return this.systemInfo.getHostVersion();
    }

    /**
     * Get the chain's type. Given as a string identifier.
     */
    public String systemChain() {
        return this.chainService.getChainSpec().getName();
    }

    /**
     * Get the chain's type.
     */
    public ChainType systemChainType() {
        return this.chainService.getChainSpec().getChainType();
    }

    /**
     * Get a custom set of properties as a JSON object, defined in the chain specification.
     */
    public Map<String, PropertyValue> systemProperties() {
        return this.chainService.getChainSpec().getProperties();
    }

    /**
     * Returns the roles the node is running as.
     */
    public String[] systemNodeRoles() {
        return new String[]{this.systemInfo.getRole()};
    }

    /**
     * Return health status of the node.
     * <p>
     * Node is considered healthy if it is:
     * <li>Connected to some peers (unless running in dev mode).</li>
     * <li>Not performing a major sync.</li>
     */
    public Map<String, Object> systemHealth() {
        return Map.ofEntries(
                entry("isSyncing", this.warpSync.isSyncing()),
                entry("peers", this.network.getPeersCount()),
                entry("shouldHavePeers", chainService.isChainLive())
        );
    }

    /**
     * Returns the base58-encoded PeerId of the node.
     */
    public String systemLocalPeerId() {
        return network.getPeerId();
    }

    /**
     * Returns the libp2p multiaddresses that the local node is listening on.
     * <p>
     * The addresses include a trailing /p2p/ with the local PeerId, and are thus
     * suitable to be passed to system_addReservedPeer or as a bootnode address for example.
     */
    public String[] systemLocalListenAddress() {
        return this.network.getListenAddresses();
    }

    /**
     * Returns currently connected peers.
     */
    public List<Map<String, Object>> systemSystemPeers() {
        return connectionManager
                .getPeerIds()
                .stream()
                .map(connectionManager::getPeerInfo)
                .map(peerInfo -> Map.<String, Object>ofEntries(
                                entry("peerId", peerInfo.getPeerId().toString()),
                                entry("roles", peerInfo.getNodeRoleName()),
                                entry("bestHash", peerInfo.getBestBlockHash().toString()),
                                entry("bestNumber", peerInfo.getBestBlock())
                        )
                ).toList();
    }

    /**
     * Adds a reserved peer. The string parameter should encode a p2p multiaddr.
     *
     * @param multiaddr Multiaddr to be added
     */
    public void systemAddReservedPeer(String multiaddr) {
        if (multiaddr == null || multiaddr.isBlank()) {
            throw new PeerNotFoundException("PeerId cannot be empty");
        }

        try {
            this.network.getKademliaService().addReservedPeer(multiaddr);
        } catch (ExecutionException e) {
            throw new ExecutionFailedException("Error while adding reserved peer: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ThreadInterruptedException(e);
        }
    }

    /**
     * Remove a reserved peer. The string should encode only the PeerId
     * e.g. QmSk5HQbn6LhUwDiNMseVUjuRYhEtYj4aUZ6WfWoGURpdV.
     *
     * @param peerIdStr peerId to be removed
     */
    public void systemRemoveReservedPeer(String peerIdStr) {
        if (peerIdStr == null || peerIdStr.isBlank()) {
            throw new PeerNotFoundException("PeerId cannot be empty");
        }
        PeerId peerId = PeerId.fromBase58(peerIdStr);

        this.connectionManager.removePeer(peerId);
    }

    /**
     * Returns the state of the syncing of the node.
     */
    public Map<String, Object> systemSyncState() {
        final BigInteger highestBlock;

        if (this.blockState.isInitialized()) {
            highestBlock = this.blockState.bestBlockNumber();
        } else {
            highestBlock = this.connectionManager
                    .getPeerIds()
                    .stream()
                    .map(this.connectionManager::getPeerInfo)
                    .map(PeerInfo::getBestBlock)
                    .max(BigInteger::compareTo)
                    .orElse(BigInteger.ZERO);
        }

        return Map.ofEntries(
                entry("startingBlock", this.syncState.getStartingBlock()),
                entry("currentBlock", this.syncState.getLastFinalizedBlockNumber()),
                entry("highestBlock", highestBlock)
        );
    }

    /**
     * Returns the next valid index (aka. nonce) for given account.
     * <p>
     * This method takes into consideration all pending transactions
     * currently in the pool and if no transactions are found in the pool
     * it fallbacks to query the index from the runtime (aka. state nonce).
     *
     * @param accountAddress the address of the account
     */
    public String systemAccountNextIndex(String accountAddress) {
        //TODO: Transcation pool and Trie needs to be implemented first.
        throw new UnsupportedOperationException("Not implemented.");
    }

    /**
     * Dry run an extrinsic. Returns a SCALE encoded ApplyExtrinsicResult.
     *
     * @param extrinsic the raw, SCALE encoded extrinsic
     * @param blockHash the block hash indicating the state. Null implies the current state
     */
    // Currently not implemented in any of the other host nodes
    @Deprecated //maybe?
    public String systemDryRun(String extrinsic, String blockHash) {
        throw new UnsupportedOperationException("Not implemented.");
    }

}
