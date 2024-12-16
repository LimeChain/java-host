package com.limechain.grandpa.state;

public enum Subround {
    PRE_VOTE(0),
    PRE_COMMIT(1),
    PRIMARY_PROPOSAL(2);

    Subround(int value) {}
}
