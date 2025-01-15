package com.limechain.network.protocol.grandpa.messages.consensus;

import com.limechain.chain.lightsyncstate.Authority;
import lombok.Data;

import java.math.BigInteger;
import java.util.List;

@Data
public class GrandpaConsensusMessage {
    private GrandpaConsensusMessageFormat format;
    private List<Authority> authorities;
    private BigInteger disabledAuthority;
    private Long delay;
    // this is denoted as 'm' in the polkadot spec
    private Long additionalOffset;
}
