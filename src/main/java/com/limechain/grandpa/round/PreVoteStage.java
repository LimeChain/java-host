package com.limechain.grandpa.round;

import com.limechain.exception.grandpa.GrandpaGenericException;
import com.limechain.grandpa.vote.SubRound;
import com.limechain.grandpa.vote.Vote;
import lombok.extern.java.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.limechain.grandpa.round.GrandpaRound.DURATION;

@Log
public class PreVoteStage implements StageState {

    @Override
    public void start(GrandpaRound round) {
        if (round.isCompletable()) {
            log.fine(String.format("Round %d is completable.", round.getRoundNumber()));
            end(round);
            return;
        }

        log.info(String.format("Round #{}: Start prevote stage", round.getRoundNumber()));
        long delay = (DURATION * 2) - (System.currentTimeMillis() - round.getStartTime().toEpochMilli());

        round.setOnStageTimerHandler(Executors.newScheduledThreadPool(1));
        round.getOnStageTimerHandler().schedule(() -> {
            log.info(String.format("Round #%d: Time of prevote stage is out", round.getRoundNumber()));
            end(round);
        }, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void end(GrandpaRound round) {
        round.clearOnStageTimerHandler();
        try {
            log.info(String.format("Round %d ended pre-vote stage", round.getRoundNumber()));
            Vote bestPreVoteCandidate = round.findBestPreVoteCandidate();
            round.broadcastVoteMessage(bestPreVoteCandidate, SubRound.PRE_VOTE);
            round.setState(new PreCommitStage());
            round.getState().start(round);
        } catch (GrandpaGenericException e) {
            log.fine(String.format("Round %d cannot end prevote stage now: %s", round.getRoundNumber(), e.getMessage()));
        }
    }
}

