package com.limechain.grandpa.state;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVote;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import io.libp2p.core.crypto.PubKey;
import lombok.Getter;
import lombok.Setter;
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
@Setter //TODO: remove it when initialize() method is implemented
@Component
public class GrandpaState {

    private static final BigInteger THRESHOLD_DENOMINATOR = BigInteger.valueOf(3);

    private List<Authority> voters;
    private BigInteger setId;
    private BigInteger roundNumber;

    //TODO: This may not be the best place for those maps
    private Map<PubKey, Vote> precommits = new ConcurrentHashMap<>();
    private Map<PubKey, Vote> prevotes = new ConcurrentHashMap<>();
    private Map<PubKey, SignedVote> pvEquivocations = new ConcurrentHashMap<>();
    private Map<PubKey, SignedVote> pcEquivocations = new ConcurrentHashMap<>();

    /**
     * The threshold is determined as the total weight of authorities
     * subtracted by the weight of potentially faulty authorities (one-third of the total weight minus one).
     *
     * @return threshold for achieving a super-majority vote
     */
    public BigInteger getThreshold() {
        var totalWeight = getAuthoritiesTotalWeight();
        var faulty = (totalWeight.subtract(BigInteger.ONE)).divide(THRESHOLD_DENOMINATOR);
        return totalWeight.subtract(faulty);
    }

    private BigInteger getAuthoritiesTotalWeight() {
        return voters.stream()
                .map(Authority::getWeight)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }
}
