package com.limechain.grandpa.round;

import lombok.extern.java.Log;

@Log
public class CompletedStage implements StageState {

    @Override
    public void start(GrandpaRound round) {
        round.setOnFinalizeHandler(null);
        round.getOnStageTimerHandler().shutdown();
        end(round);
    }

    @Override
    public void end(GrandpaRound round) {
        log.info(String.format("Round %d completed.", round.getRoundNumber()));
    }
}

