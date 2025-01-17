package com.limechain.grandpa.state;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.chain.lightsyncstate.LightSyncState;
import com.limechain.exception.grandpa.GrandpaGenericException;
import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVote;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import com.limechain.network.protocol.grandpa.messages.vote.SignedMessage;
import com.limechain.network.protocol.grandpa.messages.vote.Subround;
import com.limechain.network.protocol.grandpa.messages.vote.VoteMessage;
import com.limechain.rpc.server.AppBean;
import com.limechain.storage.DBConstants;
import com.limechain.storage.KVRepository;
import com.limechain.storage.StateUtil;
import io.emeraldpay.polkaj.types.Hash256;
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

/**
 * Represents the state information for the current round and authorities that are needed
 * for block finalization with GRANDPA.
 * Note: Intended for use only when the host is configured as an Authoring Node.
 */
@Getter
@Setter //TODO: remove it when initialize() method is implemented
@RequiredArgsConstructor
public class GrandpaSetState {

    private static final BigInteger THRESHOLD_DENOMINATOR = BigInteger.valueOf(3);
    private final KVRepository<String, Object> repository;

    private List<Authority> authorities;
    private BigInteger setId;
    private RoundCache roundCache;


    @PostConstruct
    public void initialize() {
        loadPersistedState();
        roundCache = AppBean.getBean(RoundCache.class);
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
        return repository.find(DBConstants.SET_ID, BigInteger.ONE);
    }

    public void saveLatestRound() {
        repository.save(DBConstants.LATEST_ROUND, roundCache.getLatestRound(setId));
    }

    public BigInteger fetchLatestRound() {
        return repository.find(DBConstants.LATEST_ROUND, BigInteger.ONE);
    }

    public void savePrevotes(BigInteger roundNumber) {
        GrandpaRound round = roundCache.getRound(setId, roundNumber);
        Map<Hash256, SignedVote> preVotes = round.getPreVotes();
        repository.save(StateUtil.generatePrevotesKey(DBConstants.GRANDPA_PREVOTES, roundNumber, setId), preVotes);
    }

    public Map<PubKey, Vote> fetchPrevotes(BigInteger roundNumber) {
        return repository.find(StateUtil.generatePrevotesKey(DBConstants.GRANDPA_PREVOTES, roundNumber, setId),
                Collections.emptyMap());
    }

    public void savePrecommits(BigInteger roundNumber) {
        GrandpaRound round = roundCache.getRound(setId, roundNumber);
        Map<Hash256, SignedVote> preCommits = round.getPreCommits();
        repository.save(StateUtil.generatePrecommitsKey(DBConstants.GRANDPA_PRECOMMITS, roundNumber, setId), preCommits);
    }

    public Map<PubKey, Vote> fetchPrecommits(BigInteger roundNumber) {
        return repository.find(StateUtil.generatePrecommitsKey(DBConstants.GRANDPA_PRECOMMITS, roundNumber, setId),
                Collections.emptyMap());
    }

    private void loadPersistedState() {
        this.authorities = Arrays.asList(fetchGrandpaAuthorities());
        this.setId = fetchAuthoritiesSetId();
    }

    public void persistState() {
        saveGrandpaAuthorities();
        saveAuthoritySetId();
        saveLatestRound();
        savePrecommits(roundCache.getLatestRoundNumber(setId));
        savePrevotes(roundCache.getLatestRoundNumber(setId));
    }

    public BigInteger incrementSetId() {
        this.setId = setId.add(BigInteger.ONE);
        return setId;
    }

    public void setLightSyncState(LightSyncState initState) {
        this.setId = initState.getGrandpaAuthoritySet().getSetId();
        this.authorities = Arrays.asList(initState.getGrandpaAuthoritySet().getCurrentAuthorities());
    }

    public void handleVoteMessage(VoteMessage voteMessage) {
        BigInteger voteMessageSetId = voteMessage.getSetId();
        BigInteger voteMessageRoundNumber = voteMessage.getRound();
        SignedMessage signedMessage = voteMessage.getMessage();

        SignedVote signedVote = new SignedVote();
        signedVote.setVote(new Vote(signedMessage.getBlockHash(), signedMessage.getBlockNumber()));
        signedVote.setSignature(signedMessage.getSignature());
        signedVote.setAuthorityPublicKey(signedMessage.getAuthorityPublicKey());

        GrandpaRound round = roundCache.getRound(voteMessageSetId, voteMessageRoundNumber);
        if (round == null) {
            round = new GrandpaRound();
            round.setRoundNumber(voteMessageRoundNumber);
            roundCache.addRound(voteMessageSetId, round);
        }

        Subround subround = signedMessage.getStage();
        switch (subround) {
            case PREVOTE -> round.getPreVotes().put(signedMessage.getAuthorityPublicKey(), signedVote);
            case PRECOMMIT -> round.getPreCommits().put(signedMessage.getAuthorityPublicKey(), signedVote);
            case PRIMARY_PROPOSAL -> {
                round.setPrimaryVote(signedVote);
                round.getPreVotes().put(signedMessage.getAuthorityPublicKey(), signedVote);
            }
            default -> throw new GrandpaGenericException("Unknown subround: " + subround);
        }
    }

}