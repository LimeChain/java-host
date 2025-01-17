package com.limechain.grandpa.state;

import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVote;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class GrandpaRound {

    private GrandpaRound previous;
    private BigInteger roundNumber;

    private Vote preVotedBlock;
    private Vote bestFinalCandidate;

    private Map<Hash256, SignedVote> preVotes = new ConcurrentHashMap<>();
    private Map<Hash256, SignedVote> preCommits = new ConcurrentHashMap<>();
    private SignedVote primaryVote;

    private Map<Hash256, Set<SignedVote>> pvEquivocations = new ConcurrentHashMap<>();
    private Map<Hash256, Set<SignedVote>> pcEquivocations = new ConcurrentHashMap<>();
}
