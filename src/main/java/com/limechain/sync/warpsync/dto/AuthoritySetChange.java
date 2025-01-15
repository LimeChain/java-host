package com.limechain.sync.warpsync.dto;

import com.limechain.chain.lightsyncstate.Authority;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;

@Getter
@Setter
public class AuthoritySetChange {

    private List<Authority> authorities;
    private BigInteger delay;
    private BigInteger applicationBlockNumber;

    public AuthoritySetChange(List<Authority> authorities, BigInteger delay, BigInteger announceBlockNumber) {
        this.authorities = authorities;
        this.delay = delay;
        this.applicationBlockNumber = announceBlockNumber.add(delay);
    }

    // ForcedAuthoritySetChange has priority over ScheduledAuthoritySetChange
    public static Comparator<AuthoritySetChange> getComparator() {
        return (c1, c2) -> {

            if (c1 instanceof ForcedAuthoritySetChange && !(c2 instanceof ForcedAuthoritySetChange)) {
                return -1;
            }

            if (c2 instanceof ForcedAuthoritySetChange && !(c1 instanceof ForcedAuthoritySetChange)) {
                return 1;
            }

            return 0;
        };
    }
}
