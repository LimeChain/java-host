package com.limechain.babe.coordinator;

import lombok.Getter;

import java.math.BigInteger;
import java.util.EventObject;

@Getter
public class SlotChangeEvent extends EventObject {

    private final BigInteger slotNumber;
    private final BigInteger epochIndex;
    private final boolean isLastSlotFromCurrentEpoch;

    /**
     * Constructs a prototypical Event.
     *
     * @param source the object on which the Event initially occurred
     * @param slotNumber the current slot number
     * @param epochIndex the current epoch index
     * @param isLastSlotFromCurrentEpoch A boolean flag that indicates whether the current slot is the last slot
     *                                   of the current epoch.
     *
     * @throws IllegalArgumentException if source is null
     */
    public SlotChangeEvent(Object source, BigInteger slotNumber, BigInteger epochIndex, boolean isLastSlotFromCurrentEpoch) {
        super(source);
        this.slotNumber = slotNumber;
        this.epochIndex = epochIndex;
        this.isLastSlotFromCurrentEpoch = isLastSlotFromCurrentEpoch;
    }
}
