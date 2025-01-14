package com.limechain.sync.warpsync.dto;

import com.limechain.chain.lightsyncstate.Authority;

import java.math.BigInteger;
import java.util.List;

public class ScheduledAuthoritySetChange extends AuthoritySetChange {
    public ScheduledAuthoritySetChange(Authority[] authorities, BigInteger delay) {
        super(authorities, delay);
    }

    public ScheduledAuthoritySetChange(List<Authority> authorities, BigInteger delay) {
        super(authorities, delay);
    }
}
