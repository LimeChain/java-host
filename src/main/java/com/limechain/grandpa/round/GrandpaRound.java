package com.limechain.grandpa.round;

import com.limechain.exception.grandpa.GrandpaGenericException;
import com.limechain.grandpa.vote.SignedVote;
import com.limechain.grandpa.vote.Vote;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import io.emeraldpay.polkaj.types.Hash256;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class GrandpaRound implements Serializable {

    private GrandpaRound previous;
    private BigInteger roundNumber;
    private RoundStage stage = RoundStage.INIT;

    /**
     * Current finalized block at the start of the round.
     */
    private BlockHeader lastFinalizedBlock;

    /**
     * Updated on every received pre vote message.
     * Null before first invocation of GHOST algorithm.
     */
    @Nullable
    private BlockHeader grandpaGhost;
    /**
     * Updated on every vote message received.<BR>
     * Null before first invocation of the "best final candidate" algorithm.
     */
    @Nullable
    private BlockHeader finalizeEstimate;
    /**
     * Null before the round reaches >= 2/3 + 1 pre commits.
     */
    @Nullable
    private BlockHeader finalizedBlock;

    /**
     * Best pre vote candidate.<BR>
     * Null before {@link RoundStage#PRE_VOTE_RUNS} finishes.
     */
    @Nullable
    private Vote preVoteChoice;
    /**
     * Best final candidate.<BR>
     * Null before {@link RoundStage#PRE_COMMIT_RUNS} finishes.
     */
    @Nullable
    private Vote preCommitChoice;

    private Map<Hash256, SignedVote> preVotes = new ConcurrentHashMap<>();
    private Map<Hash256, SignedVote> preCommits = new ConcurrentHashMap<>();
    private SignedVote primaryVote;

    private Map<Hash256, Set<SignedVote>> pvEquivocations = new ConcurrentHashMap<>();
    private Map<Hash256, Set<SignedVote>> pcEquivocations = new ConcurrentHashMap<>();

    private final transient List<CommitMessage> commitMessagesArchive = new ArrayList<>();

    public BlockHeader getGrandpaGhost() {
        if (grandpaGhost == null) throw new GrandpaGenericException("Grandpa GHOST has not been set.");
        return grandpaGhost;
    }

    public BlockHeader getFinalizeEstimate() {
        if (finalizeEstimate == null) throw new GrandpaGenericException("Finalize estimate has not been set.");
        return finalizeEstimate;
    }

    public BlockHeader getFinalizedBlock() {
        if (finalizedBlock == null) throw new GrandpaGenericException("Finalized block has not been set.");
        return finalizedBlock;
    }

    public Vote getPreVoteChoice() {
        if (preVoteChoice == null) throw new GrandpaGenericException("Pre-voted block has not been set");
        return preVoteChoice;
    }

    public Vote getPreCommitChoice() {
        if (preCommitChoice == null) throw new GrandpaGenericException("Best final candidate has not been set");
        return preCommitChoice;
    }

    public long getPvEquivocationsCount() {
        return this.pvEquivocations.values().stream()
                .mapToLong(Set::size)
                .sum();
    }

    public long getPcEquivocationsCount() {
        return this.pcEquivocations.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    public void addCommitMessageToArchive(CommitMessage message) {
        commitMessagesArchive.add(message);
    }

    public boolean isCommitMessageInArchive(Vote vote) {
        return commitMessagesArchive.stream()
                .anyMatch(cm -> cm.getVote().equals(vote));
    }

    public BlockHeader getBestFinalCandidate() {
        return Optional.ofNullable(finalizeEstimate).orElse(lastFinalizedBlock);
    }
}
