package com.limechain.network.protocol.grandpa;

import com.limechain.exception.scale.ScaleEncodingException;
import com.limechain.grandpa.GrandpaService;
import com.limechain.grandpa.state.GrandpaSetState;
import com.limechain.network.ConnectionManager;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceHandshakeBuilder;
import com.limechain.network.protocol.grandpa.messages.GrandpaMessageType;
import com.limechain.network.protocol.grandpa.messages.catchup.req.CatchUpReqMessage;
import com.limechain.network.protocol.grandpa.messages.catchup.req.CatchUpReqMessageScaleReader;
import com.limechain.network.protocol.grandpa.messages.catchup.res.CatchUpMessage;
import com.limechain.network.protocol.grandpa.messages.catchup.res.CatchUpMessageScaleReader;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessageScaleReader;
import com.limechain.network.protocol.grandpa.messages.neighbour.NeighbourMessage;
import com.limechain.network.protocol.grandpa.messages.neighbour.NeighbourMessageScaleReader;
import com.limechain.network.protocol.grandpa.messages.neighbour.NeighbourMessageScaleWriter;
import com.limechain.network.protocol.grandpa.messages.vote.VoteMessage;
import com.limechain.network.protocol.grandpa.messages.vote.VoteMessageScaleReader;
import com.limechain.network.protocol.message.ProtocolMessageBuilder;
import com.limechain.rpc.server.AppBean;
import com.limechain.state.AbstractState;
import com.limechain.sync.SyncMode;
import com.limechain.sync.warpsync.WarpSyncState;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Engine for handling transactions on GRANDPA streams.
 */
@Log
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class GrandpaEngine {
    private static final int HANDSHAKE_LENGTH = 1;

    private final WarpSyncState warpSyncState;

    protected ConnectionManager connectionManager;
    protected BlockAnnounceHandshakeBuilder handshakeBuilder;
    protected GrandpaSetState grandpaSetState;

    public GrandpaEngine() {
        warpSyncState = AppBean.getBean(WarpSyncState.class);

        connectionManager = ConnectionManager.getInstance();
        handshakeBuilder = new BlockAnnounceHandshakeBuilder();
        grandpaSetState = AppBean.getBean(GrandpaSetState.class);
    }

    /**
     * Handles an incoming request as follows:
     *
     * <p><b>On streams we initiated:</b>  adds streams, where we receive a handshake message,
     * to initiator streams in peer's {@link com.limechain.network.dto.PeerInfo} , ignores all other message types.
     *
     * <p><b>On responder stream: </b>
     * <p>If message payload contains a valid handshake, adds the stream when the peer is not connected already,
     * ignore otherwise. </p>
     * <p>On neighbour and commit messages, syncs received data using {@link WarpSyncState}. </p>
     * <p>Logs and ignores other message types. </p>
     *
     * @param message received message as byre array
     * @param stream  stream, where the request was received
     */
    public void receiveRequest(byte[] message, Stream stream) {
        GrandpaMessageType messageType = getGrandpaMessageType(message);

        if (messageType == null) {
            log.log(Level.WARNING, String.format("Unknown grandpa message type \"%d\" from Peer %s",
                    message[0], stream.remotePeerId()));
            return;
        }

        if (stream.isInitiator()) {
            handleInitiatorStreamMessage(message, messageType, stream);
        } else {
            handleResponderStreamMessage(message, messageType, stream);
        }
    }

    private void handleInitiatorStreamMessage(byte[] message, GrandpaMessageType messageType, Stream stream) {
        PeerId peerId = stream.remotePeerId();
        if (messageType != GrandpaMessageType.HANDSHAKE) {
            stream.close();
            log.log(Level.WARNING, "Non handshake message on initiator grandpa stream from peer " + peerId);
            return;
        }
        connectionManager.addGrandpaStream(stream);
        log.log(Level.INFO, "Received grandpa handshake from " + peerId);
        writeNeighbourMessage(stream, peerId);
    }

    private void handleResponderStreamMessage(byte[] message, GrandpaMessageType messageType, Stream stream) {
        PeerId peerId = stream.remotePeerId();
        boolean connectedToPeer = connectionManager.isGrandpaConnected(peerId);

        if (!connectedToPeer && messageType != GrandpaMessageType.HANDSHAKE) {
            log.log(Level.WARNING, "No handshake for grandpa message from Peer " + peerId);
            stream.close();
            return;
        }

        if (!SyncMode.HEAD.equals(AbstractState.getSyncMode())) {
            log.fine("Skipping grandpa message before we reach head of chain.");
            return;
        }

        switch (messageType) {
            case HANDSHAKE -> handleHandshake(message, peerId, stream);
            case VOTE -> handleVoteMessage(message, peerId);
            case COMMIT -> handleCommitMessage(message, peerId);
            case NEIGHBOUR -> handleNeighbourMessage(message, peerId);
            case CATCH_UP_REQUEST -> handleCatchupRequestMessage(message, peerId);
            case CATCH_UP_RESPONSE -> handleCatchupResponseMessage(message, peerId);
        }
    }

    private GrandpaMessageType getGrandpaMessageType(byte[] message) {
        if (message.length == HANDSHAKE_LENGTH) {
            return GrandpaMessageType.HANDSHAKE;
        }
        return GrandpaMessageType.getByType(message[0]);
    }

    private void handleHandshake(byte[] message, PeerId peerId, Stream stream) {
        if (connectionManager.isGrandpaConnected(peerId)) {
            log.log(Level.INFO, "Received existing grandpa handshake from " + peerId);
            stream.close();
        } else {
            connectionManager.addGrandpaStream(stream);
            connectionManager.getPeerInfo(peerId).setNodeRole(message[0]);
            log.log(Level.INFO, "Received grandpa handshake from " + peerId);
            writeHandshakeToStream(stream, peerId);
        }
    }

    private void handleNeighbourMessage(byte[] message, PeerId peerId) {
        ScaleCodecReader reader = new ScaleCodecReader(message);
        NeighbourMessage neighbourMessage = reader.read(NeighbourMessageScaleReader.getInstance());
        log.log(Level.FINE, "Received neighbour message from Peer " + peerId + "\n" + neighbourMessage);
        new Thread(() -> warpSyncState.syncNeighbourMessage(neighbourMessage, peerId)).start();
    }

    private void handleVoteMessage(byte[] message, PeerId peerId) {
        ScaleCodecReader reader = new ScaleCodecReader(message);
        VoteMessage voteMessage = reader.read(VoteMessageScaleReader.getInstance());
        //Maybe we need to add possible roundNumber check
        grandpaSetState.handleVoteMessage(voteMessage);
        //todo: handle vote message (authoring node responsibility?)
        log.log(Level.INFO, "Received vote message from Peer " + peerId + "\n" + voteMessage);
    }

    private void handleCommitMessage(byte[] message, PeerId peerId) {
        ScaleCodecReader reader = new ScaleCodecReader(message);
        CommitMessage commitMessage = reader.read(CommitMessageScaleReader.getInstance());
        warpSyncState.syncCommit(commitMessage, peerId);
    }

    private void handleCatchupRequestMessage(byte[] message, PeerId peerId) {
        ScaleCodecReader reader = new ScaleCodecReader(message);
        CatchUpReqMessage catchUpReqMessage = reader.read(CatchUpReqMessageScaleReader.getInstance());
        //todo: handle catchup req message (authoring node responsibility)
        log.log(Level.INFO, "Received catch up request message from Peer " + peerId + "\n" + catchUpReqMessage);
    }

    private void handleCatchupResponseMessage(byte[] message, PeerId peerId) {
        ScaleCodecReader reader = new ScaleCodecReader(message);
        CatchUpMessage catchUpMessage = reader.read(CatchUpMessageScaleReader.getInstance());
        //todo: handle catchup res message (authoring node responsibility)
        log.log(Level.INFO, "Received catch up message from Peer " + peerId + "\n" + catchUpMessage);
    }

    /**
     * Send our GRANDPA handshake on a given <b>initiator</b> stream.
     *
     * @param stream <b>initiator</b> stream to write the message to
     * @param peerId peer to send to
     */
    public void writeHandshakeToStream(Stream stream, PeerId peerId) {
        byte[] handshake = new byte[]{
                (byte) handshakeBuilder.getBlockAnnounceHandshake().getNodeRole()
        };

        log.log(Level.INFO, "Sending grandpa handshake to " + peerId);
        stream.writeAndFlush(handshake);
    }

    /**
     * Send our GRANDPA neighbour message from {@link WarpSyncState} on a given <b>responder</b> stream.
     *
     * @param stream <b>responder</b> stream to write the message to
     * @param peerId peer to send to
     */
    public void writeNeighbourMessage(Stream stream, PeerId peerId) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ScaleCodecWriter writer = new ScaleCodecWriter(buf)) {
            writer.write(NeighbourMessageScaleWriter.getInstance(), ProtocolMessageBuilder.buildNeighbourMessage());
        } catch (IOException e) {
            throw new ScaleEncodingException(e);
        }

        log.log(Level.FINE, "Sending neighbour message to peer " + peerId);
        stream.writeAndFlush(buf.toByteArray());
    }

    /**
     * Send our GRANDPA commit message from {@link GrandpaService} on a given <b>responder</b> stream.
     *
     * @param stream               <b>responder</b> stream to write the message to
     * @param encodedCommitMessage scale encoded CommitMessage object
     */
    public void writeCommitMessage(Stream stream, byte[] encodedCommitMessage) {
        log.log(Level.FINE, "Sending commit message to peer " + stream.remotePeerId());
        stream.writeAndFlush(encodedCommitMessage);
    }
}
