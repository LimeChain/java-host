package com.limechain.grandpa.state;

import com.limechain.chain.lightsyncstate.Authority;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.List;

/**
 * Represents the state information for the current round and authorities that are needed
 * for block finalization with GRANDPA.
 * Note: Intended for use only when the host is configured as an Authoring Node.
 */
@Getter
@Component
public class GrandpaState {

    private static final BigInteger THRESHOLD_NUMERATOR = BigInteger.valueOf(2);
    private static final BigInteger THRESHOLD_DENOMINATOR = BigInteger.valueOf(3);

    private List<Authority> voters;
    private BigInteger setId;
    private BigInteger roundNumber;

    public BigInteger getThreshold() {
        var votersCount = BigInteger.valueOf(voters.size());
        return THRESHOLD_NUMERATOR.multiply(votersCount)
                .divide(THRESHOLD_DENOMINATOR);
    }
}
