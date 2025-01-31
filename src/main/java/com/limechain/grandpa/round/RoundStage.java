package com.limechain.grandpa.round;

public enum RoundStage {

    //<editor-fold desc="Setup stages">

    // The round is created but has not started yet.
    INIT,

    // The round has been initialized and is beginning execution.
    START,

    //</editor-fold>

    //<editor-fold desc="Pre-vote Stages">

    // Start the pre-vote process for the round.
    START_PRE_VOTE,

    // Collect and process pre-votes.
    PRE_VOTE_RUNS,

    // Choose best pre-vote candidate and emit message.
    END_PRE_VOTE,

    //</editor-fold>

    //<editor-fold desc="Pre-commit Stages">

    // Start the pre-commit process for the round.
    START_PRE_COMMIT,

    // Collect and process pre-commits.
    PRE_COMMIT_RUNS,

    // Wait for more pre-votes until the round's GHOST >= previous round's best final candidate.
    PRE_COMMIT_WAITS_FOR_PRE_VOTES,

    // Choose pre-commit candidate and emit message.
    END_PRE_COMMIT,

    //</editor-fold>

    //<editor-fold desc="Finalization Stages">

    // The round enters a waiting state for finalization. Ends when the round is completable, finalizable and
    // this round's best final candidate >= previous round's best final candidate.
    START_WAITING,

    // The round can attempt to finalize a block.
    WAITING_RUNS,

    // The round has successfully finalized a block.
    COMPLETED;

    //</editor-fold>
}
