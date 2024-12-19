package com.limechain.grandpa;

import com.limechain.exception.storage.BlockStorageGenericException;
import com.limechain.grandpa.state.GrandpaState;
import com.limechain.grandpa.state.Subround;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import com.limechain.network.protocol.warp.dto.BlockHeader;
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

@Log
@Component
public class GrandpaService {
    private final GrandpaState grandpaState;
    private final BlockState blockState;

    public GrandpaService(GrandpaState grandpaState, BlockState blockState) {
        this.grandpaState = grandpaState;
        this.blockState = blockState;
    }

    /**
     * Finds and returns the block with the most votes in the GRANDPA prevote stage.
     * If there are multiple blocks with the same number of votes, selects the block with the highest number.
     * If no block meets the criteria, throws an exception indicating no valid GHOST candidate.
     */
    public Vote getGrandpaGHOST() {
        var threshold = grandpaState.getThreshold();
        Map<Hash256, BigInteger> blocks;

        blocks = getPossibleSelectedBlocks(threshold, Subround.PRE_COMMIT);

        if (blocks.isEmpty() || threshold.equals(BigInteger.ZERO)) {
            throw new IllegalStateException("GHOST not found.");
        }

        return selectBlockWithMostVotes(blocks);
    }

    /**
     * Selects the block with the most votes from the provided map of blocks.
     * If multiple blocks have the same number of votes, it returns the one with the highest block number.
     * Starts with the last finalized block as the initial candidate.
     */
    private Vote selectBlockWithMostVotes(Map<Hash256, BigInteger> blocks) {
        var lastFinalizedBlockHeader = blockState.getHighestFinalizedHeader();

        Vote highest = new Vote(
                lastFinalizedBlockHeader.getHash(),
                lastFinalizedBlockHeader.getBlockNumber()
        );

        for (Map.Entry<Hash256, BigInteger> entry : blocks.entrySet()) {
            Hash256 hash = entry.getKey();
            BigInteger number = entry.getValue();

            if (number.compareTo(highest.getBlockNumber()) > 0) {
                highest = new Vote(hash, number);
            }
        }

        return highest;
    }

    /**
     * Returns blocks with total votes over the threshold in a map of block hash to block number.
     * If no blocks meet the threshold directly, recursively searches their ancestors for blocks with enough votes.
     * Ancestors are included if their combined votes (including votes for their descendants) exceed the threshold.
     */
    private Map<Hash256, BigInteger> getPossibleSelectedBlocks(BigInteger threshold, Subround subround) {
        var votes = getDirectVotes(subround);
        var blocks = new HashMap<Hash256, BigInteger>();

        for (Vote vote : votes.keySet()) {
            long totalVotes = getTotalVotesForBlock(vote.getBlockHash(), subround);

            if (BigInteger.valueOf(totalVotes).compareTo(threshold) >= 0) {
                blocks.put(vote.getBlockHash(), vote.getBlockNumber());
            }
        }

        if (!blocks.isEmpty()) {
            return blocks;
        }

        List<Vote> allVotes = getVotes(subround);
        for (Vote vote : votes.keySet()) {
            blocks = new HashMap<>(
                    getPossibleSelectedAncestors(allVotes, vote.getBlockHash(), blocks, subround, threshold)
            );
        }

        return blocks;
    }

    /**
     * Returns a map of block hash to block number for ancestors meeting the threshold condition.
     * Recursively searches for ancestors with more than 2/3 votes.
     */
    public Map<Hash256, BigInteger> getPossibleSelectedAncestors(List<Vote> votes,
                                                                 Hash256 curr,
                                                                 Map<Hash256, BigInteger> selected,
                                                                 Subround subround,
                                                                 BigInteger threshold) {

        for (Vote vote : votes) {
            if (vote.getBlockHash().equals(curr)) {
                continue;
            }

            Hash256 pred;
            try {
                pred = blockState.lowestCommonAncestor(vote.getBlockHash(), curr);
            } catch (IllegalArgumentException | BlockStorageGenericException e) {
                //TODO: Refactor
                continue;
            }

            if (pred.equals(curr)) {
                return selected;
            }

            long totalVotes = getTotalVotesForBlock(pred, subround);

            if (BigInteger.valueOf(totalVotes).compareTo(threshold) >= 0) {

                BlockHeader header = blockState.getHeader(pred);
                if (header == null) {
                    //TODO: Refactor
                    throw new IllegalStateException("Header not found for block: " + pred);
                }

                selected.put(pred, header.getBlockNumber());
            } else {
                selected = getPossibleSelectedAncestors(votes, pred, selected, subround, threshold);
            }
        }

        return selected;
    }


    /**
     * Calculates the total votes for a block, including observed votes and equivocations,
     * in the specified subround.
     */
    private long getTotalVotesForBlock(Hash256 blockHash, Subround subround) {
        long votesForBlock = getObservedVotesForBlock(blockHash, subround);

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

    /**
     * Calculates the total observed votes for a block, including direct votes and votes from
     * its descendants, in the specified subround.
     */
    private long getObservedVotesForBlock(Hash256 blockHash, Subround subround) {
        var votes = getDirectVotes(subround);
        var votesForBlock = 0L;

        for (Map.Entry<Vote, Long> entry : votes.entrySet()) {
            var vote = entry.getKey();
            var count = entry.getValue();

            try {
                var isDescendant = blockState.isDescendantOf(blockHash, vote.getBlockHash());
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

    /**
     * Aggregates direct (explicit) votes for a given subround into a map of Vote to their count
     */
    private HashMap<Vote, Long> getDirectVotes(Subround subround) {
        var votes = new HashMap<Ed25519, Vote>();
        var voteCounts = new HashMap<Vote, Long>();

        switch (subround) {
            case Subround.PRE_VOTE -> votes.putAll(grandpaState.getPrevotes());
            case Subround.PRE_COMMIT -> votes.putAll(grandpaState.getPrecommits());
        }

        votes.values().forEach(vote -> voteCounts.merge(vote, 1L, Long::sum));

        return voteCounts;
    }

    private List<Vote> getVotes(Subround subround) {
        var votes = getDirectVotes(subround);
        return new ArrayList<>(votes.keySet());
    }
}
