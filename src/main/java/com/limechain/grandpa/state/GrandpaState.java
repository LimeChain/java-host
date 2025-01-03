package com.limechain.grandpa.state;

import com.limechain.chain.lightsyncstate.Authority;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.List;

/**
 * Represents the state information for the current round and authorities that are needed
 * for block finalization with GRANDPA.
 * Note: Intended for use only when the host is configured as an Authoring Node.
 */
@Getter
@Setter //TODO: remove it when initialize() method is implemented
@Component
public class GrandpaState {

    private List<Authority> voters;
    private BigInteger setId;
    private BigInteger roundNumber;

    public BigInteger derivePrimary() {
        var votersCount = BigInteger.valueOf(voters.size());
        return roundNumber.remainder(votersCount);
    }
}