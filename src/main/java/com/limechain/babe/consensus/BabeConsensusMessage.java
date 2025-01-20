package com.limechain.babe.consensus;

import com.limechain.babe.state.EpochData;
import com.limechain.babe.state.EpochDescriptor;
import lombok.Data;

import java.math.BigInteger;

@Data
public class BabeConsensusMessage {
    private EpochData nextEpochData;
    private BigInteger disabledAuthority;
    private EpochDescriptor nextEpochDescriptor;
    private BabeConsensusMessageFormat format;
}
