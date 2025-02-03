package com.limechain.grandpa.state;

import com.limechain.ServiceConsensusState;
import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.chain.lightsyncstate.LightSyncState;
import com.limechain.grandpa.round.GrandpaRound;
import com.limechain.grandpa.round.RoundCache;
import com.limechain.grandpa.vote.SignedVote;
import com.limechain.grandpa.vote.Vote;
import com.limechain.network.PeerMessageCoordinator;
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
import lombok.Setter;
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
@Setter //TODO: remove it when initialize() method is implemented
@Component
@RequiredArgsConstructor
public class GrandpaSetState extends AbstractState implements ServiceConsensusState {

    private static final BigInteger THRESHOLD_DENOMINATOR = BigInteger.valueOf(3);

    private List<Authority> authorities;
    private BigInteger disabledAuthority;
    private BigInteger setId;

    private final BlockState blockState;
    private final RoundCache roundCache;
    private final KeyStore keyStore;
    private final KVRepository<String, Object> repository;
    private final PeerMessageCoordinator messageCoordinator;

    private final PriorityQueue<AuthoritySetChange> authoritySetChanges =
            new PriorityQueue<>(AuthoritySetChange.getComparator());

    @Override
    public void populateDataFromRuntime(Runtime runtime) {
        this.authorities = runtime.getGrandpaApiAuthorities();
    }

    @Override
    public void initializeFromDatabase() {
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
        return roundCache.getLatestRoundNumber(setId).remainder(authoritiesCount);
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

    public void saveLatestRound() {
        repository.save(DBConstants.LATEST_ROUND, roundCache.getLatestRound(setId));
    }

    public GrandpaRound fetchLatestRound() {
        return repository.find(DBConstants.LATEST_ROUND, new GrandpaRound());
    }

    public void savePreVotes(BigInteger roundNumber) {
        GrandpaRound round = roundCache.getRound(setId, roundNumber);
        Map<Hash256, SignedVote> preVotes = round.getPreVotes();
        repository.save(StateUtil.generatePreVotesKey(DBConstants.GRANDPA_PREVOTES, roundNumber, setId), preVotes);
    }

    public Map<PubKey, Vote> fetchPreVotes(BigInteger roundNumber) {
        return repository.find(StateUtil.generatePreVotesKey(DBConstants.GRANDPA_PREVOTES, roundNumber, setId),
                Collections.emptyMap());
    }

    public void savePreCommits(BigInteger roundNumber) {
        GrandpaRound round = roundCache.getRound(setId, roundNumber);
        Map<Hash256, SignedVote> preCommits = round.getPreCommits();
        repository.save(StateUtil.generatePreCommitsKey(DBConstants.GRANDPA_PRECOMMITS, roundNumber, setId), preCommits);
    }

    public Map<PubKey, Vote> fetchPreCommits(BigInteger roundNumber) {
        return repository.find(StateUtil.generatePreCommitsKey(DBConstants.GRANDPA_PRECOMMITS, roundNumber, setId),
                Collections.emptyMap());
    }

    private void loadPersistedState() {
        this.setId = fetchAuthoritiesSetId();
        this.authorities = Arrays.asList(fetchGrandpaAuthorities());
    }

    @Override
    public void persistState() {
        saveGrandpaAuthorities();
        saveAuthoritySetId();
        saveLatestRound();
        savePreCommits(roundCache.getLatestRoundNumber(setId));
        savePreVotes(roundCache.getLatestRoundNumber(setId));
    }

    public void startNewSet(List<Authority> authorities) {
        this.setId = setId.add(BigInteger.ONE);
        this.authorities = authorities;

        BlockHeader lastFinalized = blockState.getHighestFinalizedHeader();

        GrandpaRound initGrandpaRound = new GrandpaRound();
        initGrandpaRound.setRoundNumber(BigInteger.ZERO);
        initGrandpaRound.setGrandpaGhost(lastFinalized);
        initGrandpaRound.setLastFinalizedBlock(lastFinalized);

        roundCache.addRound(setId, initGrandpaRound);
        // Persisting of the round happens when a block is finalized and for round ZERO we should do it manually
        persistState();

        GrandpaRound grandpaRound = new GrandpaRound();
        grandpaRound.setRoundNumber(BigInteger.ONE);

        roundCache.addRound(setId, grandpaRound);

        // TODO Move up. If we are not an authority no need to to anything in the set.
        updateAuthorityStatus();

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
            case GRANDPA_ON_DISABLED -> disabledAuthority = consensusMessage.getDisabledAuthority();
            case GRANDPA_PAUSE -> log.log(Level.SEVERE, "'PAUSE' grandpa message not implemented");
            case GRANDPA_RESUME -> log.log(Level.SEVERE, "'RESUME' grandpa message not implemented");
        }

        log.fine(String.format("Updated grandpa set config: %s", consensusMessage.getFormat().toString()));
    }

    public void handleVoteMessage(VoteMessage voteMessage) {
        BigInteger voteMessageSetId = voteMessage.getSetId();
        BigInteger voteMessageRoundNumber = voteMessage.getRound();
        SignedMessage signedMessage = voteMessage.getMessage();

        SignedVote signedVote = new SignedVote();
        signedVote.setVote(new Vote(signedMessage.getBlockHash(), signedMessage.getBlockNumber()));
        signedVote.setSignature(signedMessage.getSignature());
        signedVote.setAuthorityPublicKey(signedMessage.getAuthorityPublicKey());

        if (!isValidMessageSignature(voteMessage)) {
            log.warning(
                    String.format("Vote message signature is invalid for round %s, set %s, block hash %s, block number %s",
                            voteMessageSetId, voteMessageSetId, signedMessage.getBlockHash(), signedMessage.getBlockNumber()));
            return;
        }

        GrandpaRound round = roundCache.getRound(voteMessageSetId, voteMessageRoundNumber);
        if (round == null) {
            round = new GrandpaRound();
            round.setRoundNumber(voteMessageRoundNumber);
            roundCache.addRound(voteMessageSetId, round);
        }

        SubRound subround = signedMessage.getStage();
        switch (subround) {
            case PRE_VOTE -> round.getPreVotes().put(signedMessage.getAuthorityPublicKey(), signedVote);
            case PRE_COMMIT -> round.getPreCommits().put(signedMessage.getAuthorityPublicKey(), signedVote);
            case PRIMARY_PROPOSAL -> {
                round.setPrimaryVote(signedVote);
                round.getPreVotes().put(signedMessage.getAuthorityPublicKey(), signedVote);
            }
            default -> throw new GrandpaGenericException("Unknown subround: " + subround);
        }
    }

    private boolean isValidMessageSignature(VoteMessage voteMessage) {
        SignedMessage signedMessage = voteMessage.getMessage();

        FullVote fullVote = new FullVote();
        fullVote.setRound(voteMessage.getRound());
        fullVote.setSetId(voteMessage.getSetId());
        fullVote.setVote(new Vote(signedMessage.getBlockHash(), signedMessage.getBlockNumber()));
        fullVote.setStage(signedMessage.getStage());

        byte[] encodedFullVote = ScaleUtils.Encode.encode(FullVoteScaleWriter.getInstance(), fullVote);

        VerifySignature verifySignature = new VerifySignature(
                signedMessage.getSignature().getBytes(),
                encodedFullVote,
                signedMessage.getAuthorityPublicKey().getBytes(),
                Key.ED25519);

        return Ed25519Utils.verifySignature(verifySignature);
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