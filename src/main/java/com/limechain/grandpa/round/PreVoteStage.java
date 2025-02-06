package com.limechain.grandpa.round;

import lombok.extern.java.Log;

import java.util.concurrent.TimeUnit;

@Log
public class PreVoteStage implements StageState {

    @Override
    public void start(GrandpaRound round) {
        if (round.isCompletable()) {
            log.fine(String.format("Round %d is completable.", round.getRoundNumber()));
            end(round);
            return;
        }

        long duration = 0; //TODO: choose appropriate value
        long delay = (duration * 2) - (System.currentTimeMillis() - round.getStartTime().toEpochMilli());

        round.getOnStageTimerHandler().schedule(() -> {
            log.info(String.format("Round #%d: Time of prevote stage is out", round.getRoundNumber()));
            end(round);
        }, delay, TimeUnit.MILLISECONDS);

    }

    @Override
    public void end(GrandpaRound round) {
        log.info(String.format("Round %d ended pre-vote stage", round.getRoundNumber()));
        round.setState(new PreCommitStage());
    }

//    stage_timer_handle_ = scheduler_->scheduleWithHandle(
//        [wself{weak_from_this()}] {
//        if (auto self = wself.lock()) {
//            if (self->stage_ == Stage::PREVOTE_RUNS) {
//                SL_DEBUG(self->logger_,
//                        "Round #{}: Time of prevote stage is out",
//                        self->round_number_);
//                self->endPrevoteStage();
//            }
//        }
//    },
//    toMilliseconds(duration_ * 2 - (scheduler_->now() - start_time_)));
//
//    on_complete_handler_ = [this] {
//        if (stage_ == Stage::PREVOTE_RUNS) {
//            SL_DEBUG(logger_, "Round #{}: Became completable", round_number_);
//            endPrevoteStage();
//        }
//    };
//
//    stage_ = Stage::PREVOTE_RUNS;
}

