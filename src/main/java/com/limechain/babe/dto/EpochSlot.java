package com.limechain.babe.dto;

import lombok.Value;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

@Value
public class EpochSlot {

    Instant start;
    Duration duration;
    BigInteger number;
    BigInteger epochIndex;
}

