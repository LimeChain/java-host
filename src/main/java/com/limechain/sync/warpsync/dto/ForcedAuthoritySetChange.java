package com.limechain.sync.warpsync.dto;

import com.limechain.chain.lightsyncstate.Authority;

import java.math.BigInteger;

public class ForcedAuthoritySetChange extends AuthoritySetChange {
    public ForcedAuthoritySetChange(Authority[] authorities, BigInteger delay) {
        super(authorities, delay);
    }
}
