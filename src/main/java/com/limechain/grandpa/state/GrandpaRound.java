package com.limechain.grandpa.state;

import com.limechain.exception.grandpa.GrandpaGenericException;
import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVote;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class GrandpaRound implements Serializable {

    private GrandpaRound previous;
    private BigInteger roundNumber;

    private Vote preVotedBlock; // GHOST
    private Vote bestFinalCandidate;

    private Map<Hash256, SignedVote> preVotes = new ConcurrentHashMap<>();
    private Map<Hash256, SignedVote> preCommits = new ConcurrentHashMap<>();
    private SignedVote primaryVote;
    private BlockHeader finalizedBlockHeader;

    private Map<Hash256, Set<SignedVote>> pvEquivocations = new ConcurrentHashMap<>();
    private Map<Hash256, Set<SignedVote>> pcEquivocations = new ConcurrentHashMap<>();

    private List<CommitMessage> commitMessagesArchive = new ArrayList<>();

    public Vote getPreVotedBlock() {
        if (preVotedBlock == null) throw new GrandpaGenericException("Pre-voted block has not been set");
        return preVotedBlock;
    }

    public Vote getBestFinalCandidate() {
        if (bestFinalCandidate == null) throw new GrandpaGenericException("Best final candidate has not been set");
        return bestFinalCandidate;
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
}
