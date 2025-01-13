package com.limechain.grandpa.state;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.chain.lightsyncstate.LightSyncState;
import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVote;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import com.limechain.storage.DBConstants;
import com.limechain.storage.KVRepository;
import com.limechain.storage.StateUtil;
import io.libp2p.core.crypto.PubKey;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
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
@RequiredArgsConstructor
public class RoundState {

    private static final BigInteger THRESHOLD_DENOMINATOR = BigInteger.valueOf(3);
    private final KVRepository<String, Object> repository;

    private List<Authority> authorities;
    private BigInteger setId;
    private BigInteger roundNumber;

    //TODO: This may not be the best place for those maps
    private Map<PubKey, Vote> precommits = new ConcurrentHashMap<>();
    private Map<PubKey, Vote> prevotes = new ConcurrentHashMap<>();
    private Map<PubKey, SignedVote> pvEquivocations = new ConcurrentHashMap<>();
    private Map<PubKey, SignedVote> pcEquivocations = new ConcurrentHashMap<>();


    @PostConstruct
    public void initialize() {
        loadPersistedState();
    }

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
        return authorities.stream()
                .map(Authority::getWeight)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    public BigInteger derivePrimary() {
        var authoritiesCount = BigInteger.valueOf(authorities.size());
        return roundNumber.remainder(authoritiesCount);
    }

    public void saveGrandpaAuthorities() {
        repository.save(StateUtil.generateAuthorityKey(DBConstants.AUTHORITY_SET, setId), authorities);
    }

    public Authority[] fetchGrandpaAuthorities() {
        return repository.find(StateUtil.generateAuthorityKey(DBConstants.AUTHORITY_SET, setId), new Authority[0]);
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
        repository.save(StateUtil.generatePrevotesKey(DBConstants.GRANDPA_PREVOTES, roundNumber, setId), prevotes);
    }

    public Map<PubKey, Vote> fetchPrevotes() {
        return repository.find(StateUtil.generatePrevotesKey(DBConstants.GRANDPA_PREVOTES, roundNumber, setId),
                Collections.emptyMap());
    }

    public void savePrecommits() {
        repository.save(StateUtil.generatePrecommitsKey(DBConstants.GRANDPA_PRECOMMITS, roundNumber, setId), precommits);
    }

    public Map<PubKey, Vote> fetchPrecommits() {
        return repository.find(StateUtil.generatePrecommitsKey(DBConstants.GRANDPA_PRECOMMITS, roundNumber, setId),
                Collections.emptyMap());
    }

    private void loadPersistedState() {
        this.authorities = Arrays.asList(fetchGrandpaAuthorities());
        this.setId = fetchAuthoritiesSetId();
        this.roundNumber = fetchLatestRound();
        this.precommits = fetchPrecommits();
        this.prevotes = fetchPrevotes();
    }

    public void persistState() {
        saveGrandpaAuthorities();
        saveAuthoritySetId();
        saveLatestRound();
        savePrecommits();
        savePrevotes();
    }

    public BigInteger incrementSetId() {
        this.setId = setId.add(BigInteger.ONE);
        return setId;
    }

    public void resetRound() {
        this.roundNumber = BigInteger.ONE;
    }

    public void setLightSyncState(LightSyncState initState) {
        this.setId = initState.getGrandpaAuthoritySet().getSetId();
        this.authorities = Arrays.asList(initState.getGrandpaAuthoritySet().getCurrentAuthorities());
    }
}