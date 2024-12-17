package com.limechain.grandpa;

import com.limechain.grandpa.state.GrandpaState;
import com.limechain.grandpa.state.Subround;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import com.limechain.storage.block.BlockState;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.extern.java.Log;
import org.bouncycastle.math.ec.rfc8032.Ed25519;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log
@Component
public class GrandpaService {
    private final GrandpaState grandpaState;
    private final BlockState blockState;

    public GrandpaService(GrandpaState grandpaState, BlockState blockState) {
        this.grandpaState = grandpaState;
        this.blockState = blockState;
    }

    //TODO: Implement further
    private void getGrandpaGhost(BigInteger roundNumber){
        var threshold = grandpaState.getThreshold();

    }

    private HashMap<Hash256, BigInteger> getPossibleSelectedBlocks(Long threshold, Subround subround) {
        var votes = getDirectVotes(subround);
        var blocks = new HashMap<Hash256, BigInteger>();

        for (Vote vote : votes.keySet()) {
            long totalVotes = getTotalVotesForBlock(vote.getBlockHash(), subround);
            if (totalVotes > threshold) {
                blocks.put(vote.getBlockHash(), vote.getBlockNumber());
            }
        }

        if (!blocks.isEmpty()) {
            return blocks;
        }

        List<Vote> allVotes = getVotes(subround);
        for (Vote vote : votes.keySet()) {
            //TODO: Implement further
//            blocks = getPossibleSelectedAncestors(allVotes, v.getHash(), blocks, stage, threshold);
        }

        return blocks;
    }

    private long getTotalVotesForBlock(Hash256 blockHash, Subround subround) {
        long votesForBlock = getVotesForBlock(blockHash, subround);

        if (votesForBlock == 0L) {
            return 0L;
        }

        int equivocationCount = 0;

        switch (subround) {
            case Subround.PRE_VOTE -> equivocationCount = grandpaState.getPvEquivocations().size();
            case Subround.PRE_COMMIT -> equivocationCount = grandpaState.getPcEquivocations().size();
        }

        return votesForBlock + equivocationCount;
    }

    private long getVotesForBlock(Hash256 blockHash, Subround subround) {
        var votes = getDirectVotes(subround);
        long votesForBlock = 0L;

        for (Map.Entry<Vote, Long> entry : votes.entrySet()) {
            Vote v = entry.getKey();
            long count = entry.getValue();

            try {
                // Check if the current block is a descendant of the given block
                boolean isDescendant = blockState.isDescendantOf(blockHash, v.getBlockHash());
                if (isDescendant) {
                    votesForBlock += count;
                }

            } catch (Exception e) {
                //TODO: adjust the exception type
                return 0L;
            }
        }

        return votesForBlock;
    }

    // returns a map of votes to direct vote count
    private ConcurrentHashMap<Vote, Long> getDirectVotes(Subround subround) {
        var votes = new ConcurrentHashMap<Ed25519, Vote>();
        var voteCountMap = new ConcurrentHashMap<Vote, Long>();

        switch (subround) {
            case Subround.PRE_VOTE -> votes.putAll(grandpaState.getPrevotes());
            case Subround.PRE_COMMIT -> votes.putAll(grandpaState.getPrecommits());
        }

        votes.values().forEach(vote -> voteCountMap.merge(vote, 1L, Long::sum));

        return voteCountMap;
    }

    private List<Vote> getVotes(Subround subround) {
        var votes = getDirectVotes(subround);
        return new ArrayList<>(votes.keySet());
    }
}
