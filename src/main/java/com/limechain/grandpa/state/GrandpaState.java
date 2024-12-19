package com.limechain.grandpa.state;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVote;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import lombok.Getter;
import org.bouncycastle.math.ec.rfc8032.Ed25519;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the state information for the current round and authorities that are needed
 * for block finalization with GRANDPA.
 * Note: Intended for use only when the host is configured as an Authoring Node.
 */
@Getter
@Component
public class GrandpaState {

    private static final BigInteger THRESHOLD_DENOMINATOR = BigInteger.valueOf(3);

    private List<Authority> voters;
    private BigInteger setId;
    private BigInteger roundNumber;

    //TODO: This may not be the best place for those maps
    private Map<Ed25519, Vote> precommits = new ConcurrentHashMap<>();
    private Map<Ed25519, Vote> prevotes = new ConcurrentHashMap<>();
    private Map<Ed25519, SignedVote> pvEquivocations = new ConcurrentHashMap<>();
    private Map<Ed25519, SignedVote> pcEquivocations = new ConcurrentHashMap<>();

    /**
     * The threshold is determined as the total weight of authorities
     * divided by the weight of potentially faulty authorities (one-third of the total weight minus one).
     *
     * @return threshold for achieving a super-majority vote
     */
    public BigInteger getThreshold() {
        var totalWeight = getAuthoritiesTotalWeight();
        var faulty = (totalWeight.subtract(BigInteger.ONE)).divide(THRESHOLD_DENOMINATOR);
        return totalWeight.divide(faulty);
    }

    public BigInteger getAuthoritiesTotalWeight() {
        return voters.stream()
                .map(Authority::getWeight)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    public BigInteger derivePrimary() {
        var votersCount = BigInteger.valueOf(voters.size());
        return roundNumber.remainder(votersCount);
    }
}
