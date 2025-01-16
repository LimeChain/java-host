package com.limechain.grandpa.state;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.chain.lightsyncstate.LightSyncState;
import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVote;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import com.limechain.network.protocol.grandpa.messages.consensus.GrandpaConsensusMessage;
import com.limechain.state.AbstractState;
import com.limechain.storage.DBConstants;
import com.limechain.storage.KVRepository;
import com.limechain.storage.StateUtil;
import com.limechain.sync.warpsync.dto.AuthoritySetChange;
import com.limechain.sync.warpsync.dto.ForcedAuthoritySetChange;
import com.limechain.sync.warpsync.dto.ScheduledAuthoritySetChange;
import io.libp2p.core.crypto.PubKey;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Represents the state information for the current round and authorities that are needed
 * for block finalization with GRANDPA.
 * Note: Intended for use only when the host is configured as an Authoring Node.
 */
@Log
@Getter
@Setter //TODO: remove it when initialize() method is implemented
@Component
@RequiredArgsConstructor
public class RoundState extends AbstractState {

    private static final BigInteger THRESHOLD_DENOMINATOR = BigInteger.valueOf(3);
    private final KVRepository<String, Object> repository;

    private List<Authority> authorities;
    private BigInteger setId;
    private BigInteger roundNumber;

    private final PriorityQueue<AuthoritySetChange> authoritySetChanges = new PriorityQueue<>(AuthoritySetChange.getComparator());

    //TODO: This may not be the best place for those maps
    private Map<PubKey, Vote> precommits = new ConcurrentHashMap<>();
    private Map<PubKey, Vote> prevotes = new ConcurrentHashMap<>();
    private Map<PubKey, SignedVote> pvEquivocations = new ConcurrentHashMap<>();
    private Map<PubKey, SignedVote> pcEquivocations = new ConcurrentHashMap<>();

    @Override
    public void initialize() {
        initialized = true;
        //TODO: Find a way to initiate a runtime instance during node startup to populate from genesis.
    }

    @Override
    public void initializeFromDatabase() {
        loadPersistedState();
    }

    @Override
    public void persistState() {
        saveGrandpaAuthorities();
        saveAuthoritySetId();
        saveLatestRound();
        savePrecommits();
        savePrevotes();
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
        this.setId = fetchAuthoritiesSetId();
        this.roundNumber = fetchLatestRound();
        this.authorities = Arrays.asList(fetchGrandpaAuthorities());
        this.precommits = fetchPrecommits();
        this.prevotes = fetchPrevotes();
    }

    public void startNewSet(List<Authority> authorities) {
        this.setId = setId.add(BigInteger.ONE);
        this.roundNumber = BigInteger.ZERO;
        this.authorities = authorities;

        log.log(Level.INFO, "Successfully transitioned to authority set id: " + setId);
    }

    public void setLightSyncState(LightSyncState initState) {
        this.setId = initState.getGrandpaAuthoritySet().getSetId();
        this.authorities = Arrays.asList(initState.getGrandpaAuthoritySet().getCurrentAuthorities());
    }

    /**
     * Apply scheduled or forced authority set changes from the queue if present
     *
     * @param blockNumber required to determine if it's time to apply the change
     */
    public boolean handleAuthoritySetChange(BigInteger blockNumber) {
        AuthoritySetChange changeSetData = authoritySetChanges.peek();

        boolean updated = false;
        while (changeSetData != null) {

            if (changeSetData.getApplicationBlockNumber().compareTo(blockNumber) > 0) {
                break;
            }

            startNewSet(changeSetData.getAuthorities());
            authoritySetChanges.poll();
            updated = true;

            changeSetData = authoritySetChanges.peek();
        }

        return updated;
    }

    public void handleGrandpaConsensusMessage(GrandpaConsensusMessage consensusMessage, BigInteger currentBlockNumber) {
        switch (consensusMessage.getFormat()) {
            case GRANDPA_SCHEDULED_CHANGE -> authoritySetChanges.add(new ScheduledAuthoritySetChange(
                    consensusMessage.getAuthorities(),
                    consensusMessage.getDelay(),
                    currentBlockNumber
            ));
            case GRANDPA_FORCED_CHANGE -> authoritySetChanges.add(new ForcedAuthoritySetChange(
                    consensusMessage.getAuthorities(),
                    consensusMessage.getDelay(),
                    consensusMessage.getAdditionalOffset(),
                    currentBlockNumber
            ));
            //TODO: Implement later
            case GRANDPA_ON_DISABLED -> {
                log.log(Level.SEVERE, "'ON DISABLED' grandpa message not implemented");
            }
            case GRANDPA_PAUSE -> {
                log.log(Level.SEVERE, "'PAUSE' grandpa message not implemented");
            }
            case GRANDPA_RESUME -> {
                log.log(Level.SEVERE, "'RESUME' grandpa message not implemented");
            }
        }

        log.fine(String.format("Updated grandpa set config: %s", consensusMessage.getFormat().toString()));
    }
}