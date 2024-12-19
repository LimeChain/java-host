package com.limechain.network.protocol.grandpa.messages.consensus;

import com.limechain.chain.lightsyncstate.Authority;
import lombok.Data;

import java.math.BigInteger;
import java.util.List;

@Data
public class GrandpaConsensusMessage {
    private long bestFinalizedBlock;
    private List<Authority> authorities;
    private BigInteger disabledAuthority;
    private long delay;
    private GrandpaConsensusMessageFormat format;
}
