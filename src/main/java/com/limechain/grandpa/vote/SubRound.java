package com.limechain.grandpa.vote;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum SubRound {

    PRE_VOTE(0),
    PRE_COMMIT(1),
    PRIMARY_PROPOSAL(2),
    UNKNOWN(-1);

    SubRound(int stage) {
        this.stage = stage;
    }

    private final int stage;

    public static SubRound getByStage(int stage) {
        return Arrays.stream(values())
                .filter(t -> t.stage == stage)
                .findFirst()
                .orElse(UNKNOWN);
    }
}
