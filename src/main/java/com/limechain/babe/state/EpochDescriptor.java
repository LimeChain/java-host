package com.limechain.babe.state;

import com.limechain.chain.lightsyncstate.BabeEpoch;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.javatuples.Pair;

import java.math.BigInteger;

/**
 * Represents the BABE constant and secondary slot.
 */
@Getter
@AllArgsConstructor
public class EpochDescriptor {
    private Pair<BigInteger, BigInteger> constant;
    private BabeEpoch.BabeAllowedSlots allowedSlots;
}
