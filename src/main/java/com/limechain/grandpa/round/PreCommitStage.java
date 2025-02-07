package com.limechain.grandpa.round;

import com.limechain.exception.grandpa.GrandpaGenericException;
import com.limechain.grandpa.vote.SubRound;
import com.limechain.grandpa.vote.Vote;
import lombok.extern.java.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Log
public class PreCommitStage implements StageState {

    @Override
    public void start(GrandpaRound round) {
        log.fine(String.format("Round %d started pre-commit stage.", round.getRoundNumber()));

        if (round.isCompletable()) {
            end(round);
            return;
        }

        long timeElapsed = System.currentTimeMillis() - round.getStartTime().toEpochMilli();
        long timeRemaining = (4 * GrandpaRound.DURATION) - timeElapsed;

        round.setOnStageTimerHandler(Executors.newScheduledThreadPool(1));
        round.getOnStageTimerHandler().schedule(() -> {
            log.fine(String.format("Round %d 4 * T timer triggered.", round.getRoundNumber()));
            end(round);
        }, timeRemaining, TimeUnit.MILLISECONDS);
    }

    @Override
    public void end(GrandpaRound round) {
        log.fine(String.format("Round %d ended pre-commit stage.", round.getRoundNumber()));

        if (round.getOnStageTimerHandler() != null && !round.getOnStageTimerHandler().isShutdown()) {
            round.getOnStageTimerHandler().shutdown();
            round.setOnStageTimerHandler(null);
        }

        try {

            Vote grandpaGhost = Vote.fromBlockHeader(round.getGrandpaGhost());
            round.broadcastVoteMessage(grandpaGhost, SubRound.PRE_COMMIT);
            round.setState(new FinalizeStage());
            round.getState().start(round);
            log.fine(String.format("Round %d ended start stage.", round.getRoundNumber()));

        } catch (GrandpaGenericException e) {
            log.fine(String.format("Round %d cannot end now: %s", round.getRoundNumber(), e.getMessage()));
        }
    }
}