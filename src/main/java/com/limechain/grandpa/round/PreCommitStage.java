package com.limechain.grandpa.round;

import lombok.extern.java.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Log
public class PreCommitStage implements StageState {

    @Override
    public void start(GrandpaRound round) {
        log.fine(String.format("Round %d started pre-commit stage.", round.getRoundNumber()));

        long timeout = round.getStartTime().toEpochMilli() + (4 * GrandpaRound.DURATION);

        round.setOnStageTimerHandler(Executors.newScheduledThreadPool(1));
        round.getOnStageTimerHandler().scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();

            if (currentTime >= timeout || round.isCompletable()) {
                log.info("first log msg");
                round.getOnStageTimerHandler().shutdown();
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

    }

    @Override
    public void end(GrandpaRound round) {
    }

//    void VotingRoundImpl::startPrecommitStage() {
//        SL_DEBUG(logger_, "Round #{}: Start precommit stage", round_number_);
//
//        // Continue to receive messages
//        // until T>=Tstart + 4 * Duration or round is completable
//
//        // spec: Receive-Messages(
//        //  until Bpv>=Best-Final-Candidate(r-1)
//        //  and (Time>=Tr+4T or r is completable)
//        // )
//
//        if (completable()) {
//            SL_DEBUG(logger_, "Round #{} is already completable", round_number_);
//            stage_ = Stage::PRECOMMIT_RUNS;
//            endPrecommitStage();
//            return;
//        }
//
//        stage_timer_handle_ = scheduler_->scheduleWithHandle(
//                [wself{weak_from_this()}] {
//            if (auto self = wself.lock()) {
//                if (self->stage_ == Stage::PRECOMMIT_RUNS) {
//                    SL_DEBUG(self->logger_,
//                            "Round #{}: Time of precommit stage is out",
//                            self->round_number_);
//                    self->endPrecommitStage();
//                }
//            }
//        },
//        toMilliseconds(duration_ * 4 - (scheduler_->now() - start_time_)));
//
//        on_complete_handler_ = [this] {
//            if (stage_ == Stage::PRECOMMIT_RUNS) {
//                SL_DEBUG(logger_, "Round #{}: Became completable", round_number_);
//                endPrecommitStage();
//            }
//        };
//
//        stage_ = Stage::PRECOMMIT_RUNS;
//    }
//
//    void VotingRoundImpl::endPrecommitStage() {
//        if (stage_ == Stage::COMPLETED) {
//            return;
//        }
//        BOOST_ASSERT(stage_ == Stage::PRECOMMIT_RUNS
//                || stage_ == Stage::PRECOMMIT_WAITS_FOR_PREVOTES);
//
//        stage_timer_handle_.reset();
//
//        // https://github.com/paritytech/finality-grandpa/blob/8c45a664c05657f0c71057158d3ba555ba7d20de/src/voter/voting_round.rs#L630-L633
//        if (not prevote_ghost_) {
//            stage_ = Stage::PRECOMMIT_WAITS_FOR_PREVOTES;
//            logger_->debug("Round #{}: Precommit waits for prevotes", round_number_);
//            return;
//        }
//
//        on_complete_handler_ = nullptr;
//
//        stage_ = Stage::END_PRECOMMIT;
//
//        SL_DEBUG(logger_, "Round #{}: End precommit stage", round_number_);
//
//        // Broadcast vote for precommit stage
//        doPrecommit();
//
//        startWaitingStage();
//    }
}

