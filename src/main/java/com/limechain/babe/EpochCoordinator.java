package com.limechain.babe;

import com.limechain.babe.state.EpochState;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class EpochCoordinator {

    //TODO: Remove
    private final BabeService babeService;
    private final EpochState epochState;
    private BigInteger lastSlotNumber;
    private BigInteger lastEpochIndex;

    public EpochCoordinator(BabeService babeService, EpochState epochState) {
        this.babeService = babeService;
        this.epochState = epochState;
    }

    public void start() {
        lastSlotNumber = epochState.getCurrentSlotNumber();
        lastEpochIndex = epochState.getCurrentEpochIndex();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkAndTriggerEvents, 0, 1, TimeUnit.MILLISECONDS);
    }

    private void checkAndTriggerEvents() {
        var currentSlotNumber = epochState.getCurrentSlotNumber();
        if (lastSlotNumber.compareTo(currentSlotNumber) < 0) {
            //TODO: Replace with events
            babeService.onSlotChange();
            lastSlotNumber = currentSlotNumber;
        }

        //TODO: We want to find the slot right before the epoch change
        var currentEpochIndex = epochState.getCurrentEpochIndex();
        if (lastEpochIndex.compareTo(currentEpochIndex) < 0) {
            //TODO: Replace with events
            babeService.onEpochChange();
            lastEpochIndex = currentEpochIndex;
        }
    }
}
