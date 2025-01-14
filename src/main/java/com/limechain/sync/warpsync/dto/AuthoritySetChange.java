package com.limechain.sync.warpsync.dto;

import com.limechain.chain.lightsyncstate.Authority;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuthoritySetChange {
    private Authority[] authorities;
    private BigInteger delay;

    public AuthoritySetChange(List<Authority> authorities, BigInteger delay) {
        this(authorities.toArray(new Authority[0]), delay);
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
