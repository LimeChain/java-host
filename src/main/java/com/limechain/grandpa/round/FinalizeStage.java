package com.limechain.grandpa.round;

import com.limechain.network.protocol.warp.dto.BlockHeader;
import lombok.extern.java.Log;

@Log
public class FinalizeStage implements StageState {

    @Override
    public void start(GrandpaRound round) {

        log.fine(String.format("Round %d entered the Finalize stage.", round.getRoundNumber()));

        if (isRoundReadyToFinalized(round)) {
            end(round);
            return;
        }

        Runnable onFinalizeHandler = () -> {
            if (isRoundReadyToFinalized(round)) {
                end(round);
            }
        };
        round.setOnFinalizeHandler(onFinalizeHandler);
    }

    @Override
    public void end(GrandpaRound round) {
        log.info(String.format("Round %d met finalization conditions and will be finalized.", round.getRoundNumber()));
        round.setOnFinalizeHandler(null);
        round.attemptToFinalizeAt();
        log.fine(String.format("Round %d exits Finalize stage.", round.getRoundNumber()));

        round.end();
    }

    private boolean isRoundReadyToFinalized(GrandpaRound round) {
        BlockHeader finalized = round.getFinalizedBlock();
        BlockHeader prevBestFinalCandidate = round.getPrevBestFinalCandidate();

        if (finalized == null || prevBestFinalCandidate == null) {
            return false;
        }
        return finalized.getBlockNumber().compareTo(prevBestFinalCandidate.getBlockNumber()) >= 0;
    }
}
