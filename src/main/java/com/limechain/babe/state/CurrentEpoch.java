package com.limechain.babe.state;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigInteger;

@Getter
@AllArgsConstructor
public class CurrentEpoch {
    private BigInteger epochIndex;
    private BigInteger epochStartingSlot;
}
