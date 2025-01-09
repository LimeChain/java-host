package com.limechain.grandpa.state;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVote;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import io.libp2p.core.crypto.PubKey;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.HashMap;
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
public class RoundState {

    private static final BigInteger THRESHOLD_DENOMINATOR = BigInteger.valueOf(3);

    private List<Authority> voters;
    private BigInteger setId;
    private BigInteger roundNumber;

    private Map<PubKey, SignedVote> precommits = new ConcurrentHashMap<>();
    private Map<PubKey, SignedVote> prevotes = new ConcurrentHashMap<>();

    private Map<PubKey, List<SignedVote>> pvEquivocations = new ConcurrentHashMap<>();
    private Map<PubKey, List<SignedVote>> pcEquivocations = new ConcurrentHashMap<>();

    //TODO: Refactor if these maps are accessed/modified concurrently
    private Map<BigInteger, Vote> preVotedBlocksArchive= new HashMap<>();
    private Map<BigInteger, Vote> bestFinalCandidateArchive = new HashMap<>();

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

    public BigInteger derivePrimary() {
        var votersCount = BigInteger.valueOf(voters.size());
        return roundNumber.remainder(votersCount);
    }

    public void addPreVotedBlockToArchive(BigInteger roundNumber, Vote vote) {
        this.preVotedBlocksArchive.put(roundNumber, vote);
    }

    public void addBestFinalCandidateToArchive(BigInteger roundNumber, Vote vote) {
        this.bestFinalCandidateArchive.put(roundNumber, vote);
    }

    public Vote getBestFinalCandidateForRound(BigInteger roundNumber) {
        return this.bestFinalCandidateArchive.get(roundNumber);
    }
}