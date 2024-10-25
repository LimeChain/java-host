package com.limechain.babe.consesus;

import com.limechain.babe.state.EpochData;
import com.limechain.babe.state.EpochDescriptor;
import lombok.Data;

@Data
public class BabeConsensusMessage {
    private EpochData nextEpochData;
    private long disabledAuthority;
    private EpochDescriptor nextEpochDescriptor;
    private BabeConsensusMessageFormat format;
}
