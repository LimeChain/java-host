package com.limechain.network.protocol.grandpa;

import com.limechain.exception.grandpa.GrandpaGenericException;
import com.limechain.grandpa.round.GrandpaRound;
import com.limechain.grandpa.round.RoundCache;
import com.limechain.grandpa.state.GrandpaSetState;
import com.limechain.grandpa.vote.SignedVote;
import com.limechain.grandpa.vote.SubRound;
import com.limechain.grandpa.vote.Vote;
import com.limechain.network.PeerMessageCoordinator;
import com.limechain.network.protocol.grandpa.messages.catchup.req.CatchUpReqMessage;
import com.limechain.network.protocol.grandpa.messages.catchup.res.CatchUpResMessage;
import com.limechain.network.protocol.grandpa.messages.neighbour.NeighbourMessage;
import com.limechain.network.protocol.grandpa.messages.vote.SignedMessage;
import com.limechain.network.protocol.grandpa.messages.vote.VoteMessage;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.state.StateManager;
import io.libp2p.core.PeerId;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;

@Log
@RequiredArgsConstructor
@Component
public class GrandpaMessageHandler {
    private static final BigInteger CATCH_UP_THRESHOLD = BigInteger.TWO;
    private final StateManager stateManager;
    private final PeerMessageCoordinator messageCoordinator;


    public void handleVoteMessage(VoteMessage voteMessage) {
        RoundCache roundCache = stateManager.getGrandpaSetState().getRoundCache();
        BigInteger voteMessageSetId = voteMessage.getSetId();
        BigInteger voteMessageRoundNumber = voteMessage.getRound();
        SignedMessage signedMessage = voteMessage.getMessage();

        SignedVote signedVote = new SignedVote();
        signedVote.setVote(new Vote(signedMessage.getBlockHash(), signedMessage.getBlockNumber()));
        signedVote.setSignature(signedMessage.getSignature());
        signedVote.setAuthorityPublicKey(signedMessage.getAuthorityPublicKey());

        GrandpaRound round = roundCache.getRound(voteMessageSetId, voteMessageRoundNumber);
        if (round == null) {
            round = new GrandpaRound();
            round.setRoundNumber(voteMessageRoundNumber);
            roundCache.addRound(voteMessageSetId, round);
        }

        SubRound subround = signedMessage.getStage();
        switch (subround) {
            case PRE_VOTE -> round.getPreVotes().put(signedMessage.getAuthorityPublicKey(), signedVote);
            case PRE_COMMIT -> round.getPreCommits().put(signedMessage.getAuthorityPublicKey(), signedVote);
            case PRIMARY_PROPOSAL -> {
                round.setPrimaryVote(signedVote);
                round.getPreVotes().put(signedMessage.getAuthorityPublicKey(), signedVote);
            }
            default -> throw new GrandpaGenericException("Unknown subround: " + subround);
        }
    }

    public void initiateAndSendCatchUpRequest(NeighbourMessage neighbourMessage, PeerId peerId) {
        GrandpaSetState grandpaSetState = stateManager.getGrandpaSetState();
        // If peer has the same voter set id
        if (neighbourMessage.getSetId().equals(grandpaSetState.getSetId())) {

            // Check if needed to catch-up peer
            if (neighbourMessage.getRound().compareTo(
                    grandpaSetState.fetchLatestRound().getRoundNumber().add(CATCH_UP_THRESHOLD)) >= 0) {
                log.log(Level.FINE, "Neighbor message indicates that the round of Peer " + peerId + " is ahead.");

                CatchUpReqMessage catchUpReqMessage = CatchUpReqMessage.builder()
                        .round(neighbourMessage.getRound())
                        .setId(neighbourMessage.getSetId()).build();

                messageCoordinator.sendCatchUpRequestToPeer(peerId, catchUpReqMessage);
            }
        }
    }

    public void initiateAndSendCatchUpResponse(PeerId peerId,
                                               CatchUpReqMessage catchUpReqMessage,
                                               Supplier<Set<PeerId>> peerIds) {

        GrandpaSetState grandpaSetState = stateManager.getGrandpaSetState();
        if (!peerIds.get().contains(peerId)) {
            throw new GrandpaGenericException("Requesting catching up from a non-peer.");
        }

        if (!catchUpReqMessage.getSetId().equals(grandpaSetState.getSetId())) {
            throw new GrandpaGenericException("Catch up message has a different setId.");
        }

        if (catchUpReqMessage.getRound().compareTo(grandpaSetState.fetchLatestRound().getRoundNumber()) > 0) {
            throw new GrandpaGenericException("Catching up on a round in the future.");
        }

        GrandpaRound selectedGrandpaRound = selectRound(catchUpReqMessage.getRound(), catchUpReqMessage.getSetId())
                .orElseThrow(() -> new GrandpaGenericException("Target round was no found."));

        SignedVote[] preCommits = selectedGrandpaRound.getPreCommits().values().toArray(SignedVote[]::new);
        SignedVote[] preVotes = selectedGrandpaRound.getPreVotes().values().toArray(SignedVote[]::new);

        BlockHeader finalizedBlockHeader = selectedGrandpaRound.getFinalizedBlock();

        CatchUpResMessage catchUpResMessage = CatchUpResMessage.builder()
                .round(selectedGrandpaRound.getRoundNumber())
                .setId(grandpaSetState.getSetId())
                .preCommits(preCommits)
                .preVotes(preVotes)
                .blockHash(finalizedBlockHeader.getHash())
                .blockNumber(finalizedBlockHeader.getBlockNumber())
                .build();

        messageCoordinator.sendCatchUpResponseToPeer(peerId, catchUpResMessage);
    }

    private Optional<GrandpaRound> selectRound(BigInteger peerRoundNumber, BigInteger peerSetId) {
        GrandpaSetState grandpaSetState = stateManager.getGrandpaSetState();

        GrandpaRound round = grandpaSetState.getRoundCache().getLatestRound(grandpaSetState.getSetId());

        while (round != null) {
            // Round found
            // Check voter set
            if (round.getRoundNumber().equals(peerRoundNumber)) {
                if (round.getCommitMessagesArchive().getFirst().getSetId().equals(peerSetId)) {
                    break;
                }
            }
            // Go to the previous round
            round = round.getPrevious();
        }

        return Optional.ofNullable(round);
    }
}
