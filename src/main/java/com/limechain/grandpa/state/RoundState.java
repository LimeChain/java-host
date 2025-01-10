package com.limechain.grandpa.state;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVote;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import com.limechain.storage.DBConstants;
import com.limechain.storage.KVRepository;
import io.libp2p.core.crypto.PubKey;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.limechain.storage.StateUtil.*;

/**
 * Represents the state information for the current round and authorities that are needed
 * for block finalization with GRANDPA.
 * Note: Intended for use only when the host is configured as an Authoring Node.
 */
@Getter
@Setter //TODO: remove it when initialize() method is implemented
@Component
@RequiredArgsConstructor
public class RoundState {

    private static final BigInteger THRESHOLD_DENOMINATOR = BigInteger.valueOf(3);
    private final KVRepository<String, Object> repository;

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
        return voters.stream().map(Authority::getWeight).reduce(BigInteger.ZERO, BigInteger::add);
    }

    public BigInteger derivePrimary() {
        var votersCount = BigInteger.valueOf(voters.size());
        return roundNumber.remainder(votersCount);
    }


    public void saveGrandpaVoters() {
        repository.save(generateAuthorityKey(DBConstants.AUTHORITY_SET, setId), voters);
    }

    public Authority[] fetchGrandpaVoters() {
        return repository.find(generateAuthorityKey(DBConstants.AUTHORITY_SET, setId), new Authority[0]);
    }

    public void saveAuthoritySetId() {
        repository.save(DBConstants.SET_ID, setId);
    }

    public BigInteger fetchAuthoritiesSetId() {
        return repository.find(DBConstants.SET_ID, BigInteger.ONE);
    }

    public void saveLatestRound() {
        repository.save(DBConstants.LATEST_ROUND, roundNumber);
    }

    public BigInteger fetchLatestRound() {
        return repository.find(DBConstants.LATEST_ROUND, BigInteger.ONE);
    }

    public void savePrevotes() {
        repository.save(generatePrevotesKey(DBConstants.GRANDPA_PREVOTES, roundNumber, setId), prevotes);
    }

    public List<Map<PubKey, Vote>> fetchPrevotes() {
        return repository.find(generatePrevotesKey(DBConstants.GRANDPA_PREVOTES, roundNumber, setId),
                Collections.emptyList());
    }

    public void savePrecommits() {
        repository.save(generatePrecommitsKey(DBConstants.GRANDPA_PRECOMMITS, roundNumber, setId), precommits);
    }

    public List<Map<PubKey, Vote>> fetchPrecommits() {
        return repository.find(generatePrecommitsKey(DBConstants.GRANDPA_PRECOMMITS, roundNumber, setId),
                Collections.emptyList());
    }

}