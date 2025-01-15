package com.limechain.sync.warpsync.dto;

import com.limechain.chain.lightsyncstate.Authority;

import java.math.BigInteger;
import java.util.List;

public class ScheduledAuthoritySetChange extends AuthoritySetChange {
    public ScheduledAuthoritySetChange(List<Authority> authorities, BigInteger delay, BigInteger announceBlock) {
        super(authorities, delay, announceBlock);
    }
}
