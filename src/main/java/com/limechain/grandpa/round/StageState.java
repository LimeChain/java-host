package com.limechain.grandpa.round;

public interface StageState {

    /**
     * Checks for short circuit conditions, updates the round's handlers.
     * Might or might not invoke the {@link StageState#end(GrandpaRound)}.
     *
     * @param round The voting round context.
     */
    void start(GrandpaRound round);

    /**
     * Executes stage specific logic and transitions to next stage.
     *
     * @param round The voting round context.
     */
    void end(GrandpaRound round);
}