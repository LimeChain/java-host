package com.limechain.sync.warpsync.dto;

import com.limechain.chain.lightsyncstate.Authority;

import java.math.BigInteger;
import java.util.List;

public class ForcedAuthoritySetChange extends AuthoritySetChange {
    public ForcedAuthoritySetChange(List<Authority> authorities,
                                    BigInteger delay,
                                    BigInteger additionalOffset,
                                    BigInteger announceBlock) {

        super(authorities, delay.add(additionalOffset), announceBlock);
    }
}
