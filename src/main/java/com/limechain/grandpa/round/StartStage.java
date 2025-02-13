package com.limechain.grandpa.round;

import com.limechain.grandpa.vote.SubRound;
import com.limechain.grandpa.vote.Vote;
import lombok.extern.java.Log;

import java.time.Instant;

@Log
public class StartStage implements StageState {

    @Override
    public void start(GrandpaRound round) {

        log.fine(String.format("Round %d started.", round.getRoundNumber()));

        // TODO: Send neighbor message.

        round.setStartTime(Instant.now());

        GrandpaRound previous = round.getPrevious();
        if (round.isPrimaryVoter() && previous != null) {
            log.fine("We are a primary voter for round " + round.getRoundNumber());
            round.getPrevious().broadcastCommitMessage();

            if (previous.getBestFinalCandidate().getBlockNumber()
                    .compareTo(round.getLastFinalizedBlock().getBlockNumber()) > 0) {
                doProposal(round);
            }
        }

        end(round);
    }

    @Override
    public void end(GrandpaRound round) {

        log.fine(String.format("Round %d ended start stage.", round.getRoundNumber()));
        round.switchStage();
    }

    private void doProposal(GrandpaRound round) {

        if (round.getPrimaryVote() != null) {
            round.broadcastVoteMessage(round.getPrimaryVote(), SubRound.PRIMARY_PROPOSAL);
            return;
        }

        Vote primaryVote = Vote.fromBlockHeader(round.getPrevious().getBestFinalCandidate());
        round.setPrimaryVote(primaryVote);

        //TODO onProposal method
        round.broadcastVoteMessage(primaryVote, SubRound.PRIMARY_PROPOSAL);
    }
}

