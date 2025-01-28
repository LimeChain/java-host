package com.limechain.network;

import com.limechain.network.dto.PeerInfo;
import com.limechain.network.dto.ProtocolStreamType;
import com.limechain.network.dto.ProtocolStreams;
import com.limechain.network.protocol.blockannounce.NodeRole;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceHandshake;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * Singleton class that controls connected peer info and streams.
 */
@Log
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConnectionManager {
    private static ConnectionManager instance;
    protected final Map<PeerId, PeerInfo> peers = new HashMap<>();

    public static ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    /**
     * Gets info of a connected peer.
     *
     * @param peerId id of the peer
     * @return known peer state and connected streams
     */
    public PeerInfo getPeerInfo(PeerId peerId) {
        return peers.get(peerId);
    }

    /**
     * Adds a Block Announce stream to the peer info. Peer id is retrieved from the stream.
     *
     * @param stream stream to be added
     */
    public void addBlockAnnounceStream(Stream stream) {
        addStream(stream, ProtocolStreamType.BLOCK_ANNOUNCE);
    }

    /**
     * Adds a GRANDPA stream to the peer info. Peer id is retrieved from the stream.
     *
     * @param stream stream to be added
     */
    public void addGrandpaStream(Stream stream) {
        addStream(stream, ProtocolStreamType.GRANDPA);
    }

    public void addTransactionsStream(Stream stream) {
        addStream(stream, ProtocolStreamType.TRANSACTIONS);
    }

    private void addStream(Stream stream, ProtocolStreamType type) {
        PeerId peerId = stream.remotePeerId();
        PeerInfo peerInfo = Optional.ofNullable(peers.get(peerId)).orElseGet(() -> addNewPeer(peerId));
        ProtocolStreams protocolStreams = peerInfo.getProtocolStreams(type);

        if (stream.isInitiator()) {
            if (protocolStreams.getInitiator() != null) {
                stream.close();
                return;
            }
            protocolStreams.setInitiator(stream);
        } else {
            if (protocolStreams.getResponder() != null) {
                stream.close();
                return;
            }
            protocolStreams.setResponder(stream);
        }
    }

    public PeerInfo addNewPeer(PeerId peerId) {
        PeerInfo peerInfo = new PeerInfo();
        peerInfo.setPeerId(peerId);
        peers.put(peerId, peerInfo);
        return peerInfo;
    }

    /**
     * Removes a GRANDPA stream from the peer info. Peer id is retrieved from the stream.
     *
     * @param stream stream to be closed
     */
    public void closeGrandpaStream(Stream stream) {
        closeStream(stream, ProtocolStreamType.GRANDPA);
    }

    /**
     * Removes a Transactions stream from the peer info. Peer id is retrieved from the stream.
     *
     * @param stream stream to be closed
     */
    public void closeTransactionsStream(Stream stream) {
        closeStream(stream, ProtocolStreamType.TRANSACTIONS);
    }

    /**
     * Removes a Block Announce stream from the peer info. Peer id is retrieved from the stream.
     *
     * @param stream stream to be closed
     */
    public void closeBlockAnnounceStream(Stream stream) {
        closeStream(stream, ProtocolStreamType.BLOCK_ANNOUNCE);
    }

    private void closeStream(Stream stream, ProtocolStreamType type) {
        PeerInfo peerInfo = peers.get(stream.remotePeerId());
        if (peerInfo == null) {
            log.log(Level.WARNING, "Trying to close a missing stream for peer " + stream.remotePeerId());
            return;
        }

        ProtocolStreams protocolStreams = peerInfo.getProtocolStreams(type);

        if (stream.isInitiator()) {
            protocolStreams.setInitiator(null);
        } else {
            protocolStreams.setResponder(null);
        }
    }

    /**
     * Updates peer info (node role, genesis block hash, best block number and best block hash)
     * based on a Block Announce Handshake.
     *
     * @param peerId                 peer to be updated
     * @param blockAnnounceHandshake handshake
     */
    public void updatePeer(PeerId peerId, BlockAnnounceHandshake blockAnnounceHandshake) {
        PeerInfo peerInfo = peers.get(peerId);
        if (peerInfo == null) {
            log.log(Level.WARNING, "Trying to update missing peer " + peerId);
            return;
        }
        peerInfo.setNodeRole(blockAnnounceHandshake.getNodeRole());
        peerInfo.setGenesisBlockHash(blockAnnounceHandshake.getGenesisBlockHash());
        peerInfo.setBestBlock(blockAnnounceHandshake.getBestBlock());
        peerInfo.setBestBlockHash(blockAnnounceHandshake.getBestBlockHash());
    }

    /**
     * Updates peer info (latest block, best block number and  best block hash) based on a Block Announce Message.
     *
     * @param peerId               peer to be updated
     * @param blockAnnounceMessage message
     */
    public void updatePeer(PeerId peerId, BlockAnnounceMessage blockAnnounceMessage) {
        PeerInfo peerInfo = peers.get(peerId);
        if (peerInfo == null) {
            log.log(Level.WARNING, "Trying to update missing peer " + peerId);
            return;
        }

        BlockHeader blockHeader = blockAnnounceMessage.getHeader();
        if (blockHeader.getBlockNumber().compareTo(peerInfo.getLatestBlock()) > 0) {
            peerInfo.setLatestBlock(blockHeader.getBlockNumber());
        }
        if (blockAnnounceMessage.isBestBlock()) {
            peerInfo.setBestBlock(blockHeader.getBlockNumber());
            peerInfo.setBestBlockHash(blockHeader.getHash());
        }
    }

    /**
     * Checks if we have an open GRANDPA responder stream with a peer.
     *
     * @param peerId peer to check
     * @return do peer info and GRANDPA responder stream exist
     */
    public boolean isGrandpaConnected(PeerId peerId) {
        return peers.containsKey(peerId) && peers.get(peerId).getGrandpaStreams().getResponder() != null;
    }

    /**
     * Checks if we have an open Transactions responder stream with a peer.
     *
     * @param peerId peer to check
     * @return do peer info and Transaction responder stream exist
     */
    public boolean isTransactionsConnected(PeerId peerId) {
        return peers.containsKey(peerId) && peers.get(peerId).getTransactionsStreams().getResponder() != null;
    }

    /**
     * Checks if we have an open Block Announce responder stream with a peer.
     *
     * @param peerId peer to check
     * @return do peer info and Block Announce responder stream exist
     */
    public boolean isBlockAnnounceConnected(PeerId peerId) {
        return peers.containsKey(peerId) && peers.get(peerId).getBlockAnnounceStreams().getResponder() != null;
    }

    /**
     * Gets the ids of all peers with open connections.
     * Open connection means either Grandpa or Block Announce stream has been opened.
     *
     * @return set of connected peer ids
     */
    public Set<PeerId> getPeerIds() {
        return peers.keySet();
    }

    /**
     * Closes conneciton to all the connected peers and removes them from the peersList.
     */
    public void removeAllPeers() {
        peers.forEach((peerId, peerInfo) -> {
            closeProtocolStream(peerInfo.getBlockAnnounceStreams());
            closeProtocolStream(peerInfo.getGrandpaStreams());
        });
        peers.clear();
    }

    /**
     * Closes conneciton to a peer and removes it from the peersList.
     *
     * @param peerId peerId of the peer to be removed
     */
    public void removePeer(PeerId peerId) {
        if (peers.containsKey(peerId)) {
            PeerInfo peerInfo = peers.get(peerId);
            closeProtocolStream(peerInfo.getBlockAnnounceStreams());
            closeProtocolStream(peerInfo.getGrandpaStreams());
            peers.remove(peerId);
        }
    }

    private void closeProtocolStream(final ProtocolStreams streams) {
        if (streams == null) return;
        if (streams.getInitiator() != null) {
            streams.getInitiator().close();
        }
        if (streams.getResponder() != null) {
            streams.getResponder().close();
        }
    }

    public boolean checkIfPeerIsAuthorNode(PeerId peerId) {
        return NodeRole.AUTHORING.getValue().equals(getPeerInfo(peerId).getNodeRole());
    }
}
