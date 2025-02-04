package com.limechain.grandpa.round;

import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
public class PlayRoundMachine {

    private final GrandpaRound round;

    @Setter
    private StageState state = new StartStage();

    public void play() {
        state.start(round);
    }

    public void end() {
        state = new CompletedStage();
        state.start(round);
    }
}
