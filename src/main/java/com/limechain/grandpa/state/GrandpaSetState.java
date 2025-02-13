package com.limechain.grandpa.state;

import com.limechain.ServiceConsensusState;
import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.chain.lightsyncstate.LightSyncState;
import com.limechain.exception.grandpa.GrandpaGenericException;
import com.limechain.grandpa.round.GrandpaRound;
import com.limechain.grandpa.vote.SignedVote;
import com.limechain.grandpa.vote.Vote;
import com.limechain.network.protocol.grandpa.messages.consensus.GrandpaConsensusMessage;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.runtime.Runtime;
import com.limechain.state.AbstractState;
import com.limechain.storage.DBConstants;
import com.limechain.storage.KVRepository;
import com.limechain.storage.StateUtil;
import com.limechain.storage.block.state.BlockState;
import com.limechain.storage.crypto.KeyStore;
import com.limechain.storage.crypto.KeyType;
import com.limechain.sync.warpsync.dto.AuthoritySetChange;
import com.limechain.sync.warpsync.dto.ForcedAuthoritySetChange;
import com.limechain.sync.warpsync.dto.ScheduledAuthoritySetChange;
import io.emeraldpay.polkaj.types.Hash256;
import io.libp2p.core.crypto.PubKey;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.javatuples.Pair;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.logging.Level;

/**
 * Represents the state information for the current round and authorities that are needed
 * for block finalization with GRANDPA.
 * Note: Intended for use only when the host is configured as an Authoring Node.
 */
@Log
@Getter
@Component
@RequiredArgsConstructor
public class GrandpaSetState extends AbstractState implements ServiceConsensusState {

    private static final BigInteger THRESHOLD_DENOMINATOR = BigInteger.valueOf(3);

    private List<Authority> authorities;
    private BigInteger disabledAuthority;
    private BigInteger setId;

    private final BlockState blockState;
    private final KeyStore keyStore;
    private final KVRepository<String, Object> repository;

    private final PriorityQueue<AuthoritySetChange> authoritySetChanges =
            new PriorityQueue<>(AuthoritySetChange.getComparator());

    private GrandpaRound currentGrandpaRound;

    @Override
    public void populateDataFromRuntime(Runtime runtime) {
        this.authorities = runtime.getGrandpaApiAuthorities();
    }

    @Override
    public void initializeFromDatabase() {
        loadPersistedState();
    }

    // persists data connected to the current round which may not be finalized
    @Override
    public void persistState() {
        saveGrandpaAuthorities();
        saveAuthoritySetId();
        saveLatestRoundNumber(getCurrentGrandpaRound().getRoundNumber());
        savePreCommits(getCurrentGrandpaRound().getRoundNumber());
        savePreVotes(getCurrentGrandpaRound().getRoundNumber());
    }

    // Persisting of the round data should happen when a round is finalized
    // Round 0 from every set is finalized instantly after creation
    public void persistFinalizedRoundState(BigInteger roundNumber) {
        saveLatestRoundNumber(roundNumber);
        savePreCommits(roundNumber);
        savePreVotes(roundNumber);
    }

    // persists set data into the database
    public void persistNewSetState() {
        saveAuthoritySetId();
        saveGrandpaAuthorities();
    }

    /**
     * The threshold is determined as the total weight of authorities
     * subtracted by the weight of potentially faulty authorities (one-third of the total weight minus one).
     *
     * @return threshold for achieving a super-majority vote
     */
    public BigInteger getThreshold(List<Authority> authorities) {
        var totalWeight = getAuthoritiesTotalWeight(authorities);
        var faulty = (totalWeight.subtract(BigInteger.ONE)).divide(THRESHOLD_DENOMINATOR);
        return totalWeight.subtract(faulty);
    }


    public BigInteger getAuthoritiesTotalWeight(List<Authority> authorities) {
        return authorities.stream()
                .map(Authority::getWeight)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    public BigInteger derivePrimary(BigInteger roundNumber) {
        var authoritiesCount = BigInteger.valueOf(authorities.size());
        return roundNumber.remainder(authoritiesCount);
    }

    public void startNewSet(List<Authority> authorities) {

        this.setId = setId != null ? setId.add(BigInteger.ONE) : BigInteger.ONE;
        this.authorities = authorities;

        persistNewSetState();

        updateAuthorityStatus();

        if (AbstractState.isActiveAuthority()) {
            BlockHeader lastFinalized = blockState.getHighestFinalizedHeader();

            GrandpaRound initGrandpaRound = new GrandpaRound(
                    currentGrandpaRound,
                    BigInteger.ZERO,
                    setId,
                    authorities,
                    getThreshold(authorities),
                    false,
                    lastFinalized
            );

            initGrandpaRound.setGrandpaGhost(lastFinalized);

            addNewGrandpaRound(initGrandpaRound);

            persistFinalizedRoundState(initGrandpaRound.getRoundNumber());

            BigInteger primaryIndex = derivePrimary(BigInteger.ONE);
            boolean isPrimary = Arrays.equals(authorities.get(primaryIndex.intValueExact()).getPublicKey(),
                    AbstractState.getGrandpaKeyPair().getValue0());

            GrandpaRound grandpaRound = new GrandpaRound(
                    currentGrandpaRound,
                    BigInteger.ONE,
                    setId,
                    authorities,
                    getThreshold(authorities),
                    isPrimary,
                    lastFinalized
            );

            addNewGrandpaRound(grandpaRound);

            log.log(Level.INFO, "Successfully transitioned to authority set id: " + setId);
        }
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
            case GRANDPA_ON_DISABLED -> disabledAuthority = consensusMessage.getDisabledAuthority();
            case GRANDPA_PAUSE -> log.log(Level.SEVERE, "'PAUSE' grandpa message not implemented");
            case GRANDPA_RESUME -> log.log(Level.SEVERE, "'RESUME' grandpa message not implemented");
        }

        log.fine(String.format("Updated grandpa set config: %s", consensusMessage.getFormat().toString()));
    }

    // We keep a maximum of 3 rounds at a time
    public synchronized void addNewGrandpaRound(GrandpaRound grandpaRound) {
        if (currentGrandpaRound.getPrevious() != null && currentGrandpaRound.getPrevious().getPrevious() != null) {
            // Setting the previous to null make it
            currentGrandpaRound.getPrevious().setPrevious(null);
        }

        currentGrandpaRound = grandpaRound;
    }

    public GrandpaRound getGrandpaRound(BigInteger roundNumber) {

        GrandpaRound current = currentGrandpaRound;
        while (!current.getRoundNumber().equals(roundNumber)) {

            current = current.getPrevious();
            if (current == null) {
                throw new GrandpaGenericException("Target round not found");
            }
        }

        return current;
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
        return repository.find(DBConstants.SET_ID, BigInteger.ZERO);
    }

    public void saveLatestRoundNumber(BigInteger roundNumber) {
        repository.save(DBConstants.LATEST_ROUND, roundNumber);
    }

    public BigInteger fetchLatestRoundNumber() {
        return repository.find(DBConstants.LATEST_ROUND, BigInteger.ZERO);
    }

    public void savePreVotes(BigInteger roundNumber) {
        GrandpaRound round = getGrandpaRound(roundNumber);
        Map<Hash256, SignedVote> preVotes = round.getPreVotes();
        repository.save(StateUtil.generatePreVotesKey(DBConstants.GRANDPA_PREVOTES, roundNumber, setId), preVotes);
    }

    public Map<PubKey, Vote> fetchPreVotes(BigInteger roundNumber) {
        return repository.find(StateUtil.generatePreVotesKey(DBConstants.GRANDPA_PREVOTES, roundNumber, setId),
                Collections.emptyMap());
    }

    public void savePreCommits(BigInteger roundNumber) {
        GrandpaRound round = getGrandpaRound(roundNumber);
        Map<Hash256, SignedVote> preCommits = round.getPreCommits();
        repository.save(StateUtil.generatePreCommitsKey(DBConstants.GRANDPA_PRECOMMITS, roundNumber, setId), preCommits);
    }

    public Map<PubKey, Vote> fetchPreCommits(BigInteger roundNumber) {
        return repository.find(StateUtil.generatePreCommitsKey(DBConstants.GRANDPA_PRECOMMITS, roundNumber, setId),
                Collections.emptyMap());
    }

    public Optional<BigInteger> getAuthorityWeight(Hash256 authorityPublicKey) {
        return authorities.stream()
                .filter(authority -> new Hash256(authority.getPublicKey()).equals(authorityPublicKey))
                .map(Authority::getWeight)
                .findFirst();
    }

    private void loadPersistedState() {
        this.setId = fetchAuthoritiesSetId();
        this.authorities = Arrays.asList(fetchGrandpaAuthorities());
    }

    private void updateAuthorityStatus() {
        Optional<Pair<byte[], byte[]>> keyPair = authorities.stream()
                .map(a -> keyStore.getKeyPair(KeyType.GRANDPA, a.getPublicKey()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        keyPair.ifPresentOrElse(AbstractState::setAuthorityStatus, AbstractState::clearAuthorityStatus);
    }
}