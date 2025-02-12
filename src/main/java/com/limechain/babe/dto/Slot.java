package com.limechain.babe.dto;

import lombok.Value;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

@Value
public class Slot implements Serializable {

    Instant start;
    Duration duration;
    BigInteger number;
    BigInteger epochIndex;
}

