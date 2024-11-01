package com.limechain.babe.state;

import com.limechain.chain.lightsyncstate.Authority;
import lombok.Builder;
import lombok.Getter;

import java.math.BigInteger;
import java.util.List;

@Getter
@Builder
public class CurrentEpoch {
    private BigInteger epochIndex;
    private BigInteger epochStartingSlot;
    private BigInteger epochLength;
    private List<Authority> authorityList;
    private byte[] randomness;
}
