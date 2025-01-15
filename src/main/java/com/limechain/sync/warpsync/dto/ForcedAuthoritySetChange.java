package com.limechain.sync.warpsync.dto;

import com.limechain.chain.lightsyncstate.Authority;

import java.math.BigInteger;
import java.util.List;

public class ForcedAuthoritySetChange extends AuthoritySetChange {
    public ForcedAuthoritySetChange(List<Authority> authorities,
                                    Long delay,
                                    Long additionalOffset,
                                    BigInteger announceBlock) {

        super(authorities, delay + additionalOffset, announceBlock);
    }
}
