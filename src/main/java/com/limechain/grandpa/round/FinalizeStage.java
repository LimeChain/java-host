package com.limechain.grandpa.round;

import com.limechain.exception.grandpa.GrandpaGenericException;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import lombok.extern.java.Log;

@Log
public class FinalizeStage implements StageState {

    @Override
    public void start(GrandpaRound round) {

        log.fine(String.format("Round %d entered the Finalize stage.", round.getRoundNumber()));

        if (isRoundReadyToBeFinalized(round)) {
            end(round);
            return;
        }

        Runnable onFinalizeHandler = () -> {
            if (isRoundReadyToBeFinalized(round)) {
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

    private boolean isRoundReadyToBeFinalized(GrandpaRound round) {
        BlockHeader finalized;
        try {
            finalized = round.getFinalizedBlock();
        } catch (GrandpaGenericException e) {
            return false;
        }

        BlockHeader prevBestFinalCandidate = round.getPrevBestFinalCandidate();

        return prevBestFinalCandidate != null
                && finalized.getBlockNumber().compareTo(prevBestFinalCandidate.getBlockNumber()) >= 0;
    }
}
