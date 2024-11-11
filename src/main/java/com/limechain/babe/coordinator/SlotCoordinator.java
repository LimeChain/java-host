package com.limechain.babe.coordinator;

import com.limechain.babe.state.EpochState;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class SlotCoordinator {

    private final List<SlotChangeListener> slotChangeListenerList = new ArrayList<>();
    private final EpochState epochState;

    private BigInteger lastSlotNumber;
    private BigInteger lastSlotOfCurrentEpoch;

    public SlotCoordinator(EpochState epochState) {
        this.epochState = epochState;
    }

    public void start(List<SlotChangeListener> listeners) {
        this.slotChangeListenerList.addAll(listeners);

        lastSlotNumber = epochState.getCurrentSlotNumber();
        lastSlotOfCurrentEpoch = epochState.getCurrentEpochEndSlotNumber();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkAndTriggerEvent, 0, 1, TimeUnit.MILLISECONDS);
    }

    private void notifySlotChangeListeners(SlotChangeEvent event) {
        for (SlotChangeListener slotChangeListener : slotChangeListenerList) {
            slotChangeListener.slotChanged(event);
        }
    }

    private void checkAndTriggerEvent() {
        var currentSlotNumber = epochState.getCurrentSlotNumber();
        var currentEpochIndex = epochState.getCurrentEpochIndex();

        if (hasSlotChanged(currentSlotNumber)) {
            var isLastSlot = isLastSlotFromCurrentEpoch(currentSlotNumber);
            triggerEvent(currentSlotNumber, currentEpochIndex, isLastSlot);
            updateLocalSlotState(currentSlotNumber);
        }
    }

    private boolean hasSlotChanged(BigInteger currentSlotNumber) {
        return currentSlotNumber.compareTo(lastSlotNumber) > 0;
    }

    private boolean isLastSlotFromCurrentEpoch(BigInteger currentSlotNumber) {
        return currentSlotNumber.compareTo(lastSlotOfCurrentEpoch) == 0;
    }

    private void triggerEvent(BigInteger currentSlotNumber, BigInteger currentEpochIndex, boolean isLastSlot) {
        var event = new SlotChangeEvent(
                this,
                currentSlotNumber,
                currentEpochIndex,
                isLastSlot
        );

        notifySlotChangeListeners(event);
    }

    private void updateLocalSlotState(BigInteger currentSlotNumber) {
        lastSlotNumber = currentSlotNumber;

        // Add epoch length to current epoch last slot number to get the next epoch last slot
        if (currentSlotNumber.compareTo(lastSlotOfCurrentEpoch) > 0) {
            lastSlotOfCurrentEpoch = lastSlotOfCurrentEpoch.add(epochState.getEpochLength());
        }
    }
}
