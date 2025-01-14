package com.limechain.sync.warpsync.dto;

import com.limechain.chain.lightsyncstate.Authority;

import java.math.BigInteger;
import java.util.List;

public class ForcedAuthoritySetChange extends AuthoritySetChange {
    public ForcedAuthoritySetChange(Authority[] authorities, BigInteger delay) {
        super(authorities, delay);
    }

    public ForcedAuthoritySetChange(List<Authority> authorities, BigInteger delay) {
        super(authorities, delay);
    }
}
