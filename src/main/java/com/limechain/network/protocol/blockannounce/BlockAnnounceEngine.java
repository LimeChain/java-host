package com.limechain.network.protocol.blockannounce;

import com.limechain.babe.state.EpochState;
import com.limechain.exception.scale.ScaleEncodingException;
import com.limechain.network.ConnectionManager;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceHandshake;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceHandshakeBuilder;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.blockannounce.scale.BlockAnnounceHandshakeScaleReader;
import com.limechain.network.protocol.blockannounce.scale.BlockAnnounceHandshakeScaleWriter;
import com.limechain.network.protocol.blockannounce.scale.BlockAnnounceMessageScaleReader;
import com.limechain.network.protocol.warp.dto.ConsensusEngine;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.rpc.server.AppBean;
import com.limechain.storage.block.BlockState;
import com.limechain.sync.warpsync.WarpSyncState;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;

@Log
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class BlockAnnounceEngine {

    public static final int HANDSHAKE_LENGTH = 69;

    protected ConnectionManager connectionManager;
    protected WarpSyncState warpSyncState;
    protected BlockAnnounceHandshakeBuilder handshakeBuilder;
    protected EpochState epochState;

    public BlockAnnounceEngine() {
        connectionManager = ConnectionManager.getInstance();
        warpSyncState = AppBean.getBean(WarpSyncState.class);
        handshakeBuilder = new BlockAnnounceHandshakeBuilder();
        epochState = AppBean.getBean(EpochState.class);
    }

    public void receiveRequest(byte[] msg, Stream stream) {
        PeerId peerId = stream.remotePeerId();
        boolean connectedToPeer = connectionManager.isBlockAnnounceConnected(peerId);
        boolean isHandshake = msg.length == HANDSHAKE_LENGTH;

        if (!connectedToPeer && !isHandshake) {
            log.log(Level.WARNING, "No handshake for block announce message from Peer " + peerId);
            return;
        }

        if (isHandshake) {
            handleHandshake(msg, peerId, stream, connectedToPeer);
        } else {
            handleBlockAnnounce(msg, peerId);
        }

        //TODO: Send message to network? module
    }

    private void handleHandshake(byte[] msg, PeerId peerId, Stream stream, boolean connectedToPeer) {
        /*  We might not need to send a second handshake.
        If we already have stored the key it means that we have processed the handshake once.
        This might be caused by using a different stream for sending and receiving in libp2p.
        */
        if (connectedToPeer) {
            log.log(Level.INFO, "Received existing handshake from " + peerId);
            stream.close();
        } else {
            ScaleCodecReader reader = new ScaleCodecReader(msg);
            BlockAnnounceHandshake handshake = reader.read(new BlockAnnounceHandshakeScaleReader());
            connectionManager.addBlockAnnounceStream(stream);
            connectionManager.updatePeer(peerId, handshake);
            log.log(Level.INFO, "Received handshake from " + peerId + "\n" +
                    handshake);
            writeHandshakeToStream(stream, peerId);
        }
    }

    private void handleBlockAnnounce(byte[] msg, PeerId peerId) {
        BlockAnnounceMessage announce = ScaleUtils.Decode.decode(msg, new BlockAnnounceMessageScaleReader());
        connectionManager.updatePeer(peerId, announce);
        //TODO Yordan: Do we actually need this since each block has a runtime?
        warpSyncState.syncBlockAnnounce(announce);
        log.log(Level.FINE, "Received block announce for block #" + announce.getHeader().getBlockNumber() +
                " from " + peerId +
                " with hash:0x" + announce.getHeader().getHash() +
                " parentHash:" + announce.getHeader().getParentHash() +
                " stateRoot:" + announce.getHeader().getStateRoot());

        if (BlockState.getInstance().isInitialized()) {
            BlockState.getInstance().addBlockToQueue(announce.getHeader());
        }
        updateEpochStateIfBabeConsensusMessageExists(announce);
    }

    //TODO: We would need DigestHelper class that would encapsulate logic needed for all types message handling HeaderDigest[]
    public void updateEpochStateIfBabeConsensusMessageExists(BlockAnnounceMessage announce) {
        HeaderDigest[] headerDigests = announce.getHeader().getDigest();
        if (headerDigests != null && headerDigests.length > 0) {
            Arrays.stream(headerDigests)
                    .filter(headerDigest -> DigestType.CONSENSUS_MESSAGE.equals(headerDigest.getType()) &&
                            ConsensusEngine.BABE.equals(headerDigest.getId()))
                    .findFirst()
                    .map(HeaderDigest::getMessage)
                    .ifPresent(message -> epochState.updateNextEpochBlockConfig(message));
        }
    }

    public void writeHandshakeToStream(Stream stream, PeerId peerId) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ScaleCodecWriter writer = new ScaleCodecWriter(buf)) {
            writer.write(new BlockAnnounceHandshakeScaleWriter(), handshakeBuilder.getBlockAnnounceHandshake());
        } catch (IOException e) {
            throw new ScaleEncodingException(e);
        }

        log.log(Level.INFO, "Sending handshake to " + peerId);
        stream.writeAndFlush(buf.toByteArray());
    }
}
