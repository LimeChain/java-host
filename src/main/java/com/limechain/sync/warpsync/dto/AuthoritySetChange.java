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
    private Long delay;
    private BigInteger applicationBlock;

    public AuthoritySetChange(List<Authority> authorities, Long delay, BigInteger announceBlock) {
        this.authorities = authorities;
        this.delay = delay;
        this.applicationBlock = announceBlock.add(BigInteger.valueOf(delay));
    }

    // ForcedAuthoritySetChange has priority over ScheduledAuthoritySetChanges
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
