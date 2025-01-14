package com.limechain.sync.warpsync.dto;

import com.limechain.chain.lightsyncstate.Authority;

import java.math.BigInteger;

public class ScheduledAuthoritySetChange extends AuthoritySetChange {
    public ScheduledAuthoritySetChange(Authority[] authorities, BigInteger delay) {
        super(authorities, delay);
    }
}
