package com.limechain.network.protocol.blockannounce;

import com.limechain.exception.scale.ScaleEncodingException;
import com.limechain.network.ConnectionManager;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceHandshake;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceHandshakeBuilder;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.blockannounce.scale.BlockAnnounceHandshakeScaleReader;
import com.limechain.network.protocol.blockannounce.scale.BlockAnnounceHandshakeScaleWriter;
import com.limechain.network.protocol.blockannounce.scale.BlockAnnounceMessageScaleReader;
import com.limechain.rpc.server.AppBean;
import com.limechain.storage.block.BlockHandler;
import com.limechain.storage.block.state.BlockState;
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
import java.time.Instant;
import java.util.logging.Level;

@Log
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class BlockAnnounceEngine {

    public static final int HANDSHAKE_LENGTH = 69;

    protected ConnectionManager connectionManager;
    protected WarpSyncState warpSyncState;
    private BlockHandler blockHandler;
    protected BlockAnnounceHandshakeBuilder handshakeBuilder;

    public BlockAnnounceEngine() {
        connectionManager = ConnectionManager.getInstance();
        warpSyncState = AppBean.getBean(WarpSyncState.class);
        blockHandler = AppBean.getBean(BlockHandler.class);
        handshakeBuilder = new BlockAnnounceHandshakeBuilder();
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
                " with hash:" + announce.getHeader().getHash() +
                " parentHash:" + announce.getHeader().getParentHash() +
                " stateRoot:" + announce.getHeader().getStateRoot());

        if (AppBean.getBean(BlockState.class).isInitialized()) {
            //TODO Network improvements: Block requests should be sent to the peer that announced the block itself.
            blockHandler.handleBlockHeader(Instant.now(), announce.getHeader(), peerId);
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

    public void writeBlockAnnounceMessage(Stream stream, PeerId peerId, byte[] encodedBlockAnnounceMessage) {
        log.log(Level.FINE, "Sending Block Announce message to peer " + peerId);
        stream.writeAndFlush(encodedBlockAnnounceMessage);
    }
}
