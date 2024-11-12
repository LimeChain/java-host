package com.limechain.babe.coordinator;

import com.limechain.babe.dto.EpochSlot;
import lombok.Getter;

import java.util.EventObject;

@Getter
public class SlotChangeEvent extends EventObject {

    private final EpochSlot epochSlot;
    private final boolean isLastSlotFromCurrentEpoch;

    /**
     * Constructs a prototypical Event.
     *
     * @param source                     the object on which the Event initially occurred
     * @param epochSlot                  The new slot that triggered the event.
     * @param isLastSlotFromCurrentEpoch A boolean flag that indicates whether the current slot is the last slot
     *                                   of the current epoch.
     * @throws IllegalArgumentException if source is null
     */
    public SlotChangeEvent(Object source, EpochSlot epochSlot, boolean isLastSlotFromCurrentEpoch) {
        super(source);

        this.isLastSlotFromCurrentEpoch = isLastSlotFromCurrentEpoch;
        this.epochSlot = epochSlot;
    }
}
