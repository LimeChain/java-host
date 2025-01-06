package com.limechain.grandpa;

import com.limechain.exception.grandpa.GhostExecutionException;
import com.limechain.exception.storage.BlockStorageGenericException;
import com.limechain.grandpa.state.RoundState;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import com.limechain.network.protocol.grandpa.messages.vote.Subround;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.storage.block.BlockState;
import io.emeraldpay.polkaj.types.Hash256;
import io.libp2p.core.crypto.PubKey;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log
@Component
public class GrandpaService {

    private final RoundState roundState;
    private final BlockState blockState;

    public GrandpaService(RoundState roundState, BlockState blockState) {
        this.roundState = roundState;
        this.blockState = blockState;
    }

    public Vote getBestFinalCandidate() {

        Vote preVoteCandidate = getGrandpaGhost();

        var threshold = roundState.getThreshold();
        Map<Hash256, BigInteger> possibleSelectedBlocks = getPossibleSelectedBlocks(threshold, Subround.PRECOMMIT);

        if (possibleSelectedBlocks.isEmpty()) {
            return preVoteCandidate;
        }

        var bestFinalCandidate = getLastFinalizedBlockAsVote();

        for (Map.Entry<Hash256, BigInteger> block : possibleSelectedBlocks.entrySet()) {

            var blockHash = block.getKey();
            var blockNumber = block.getValue();

            boolean isDescendant = blockState.isDescendantOf(blockHash, preVoteCandidate.getBlockHash());

            if (!isDescendant) {

                Hash256 lowestCommonAncestor;
                try {
                    lowestCommonAncestor = blockState.lowestCommonAncestor(blockHash, preVoteCandidate.getBlockHash());
                } catch (IllegalArgumentException e) {
                    log.warning("Error finding the lowest common ancestor: " + e.getMessage());
                    continue;
                }

                BlockHeader header = blockState.getHeader(lowestCommonAncestor);
                blockNumber = header.getBlockNumber();
                blockHash = lowestCommonAncestor;
            }

            if (blockNumber.compareTo(bestFinalCandidate.getBlockNumber()) > 0) {
                bestFinalCandidate = new Vote(
                        blockHash,
                        blockNumber
                );
            }
        }

        return bestFinalCandidate;
    }

    /**
     * Finds and returns the block with the most votes in the GRANDPA prevote stage.
     * If there are multiple blocks with the same number of votes, selects the block with the highest number.
     * If no block meets the criteria, throws an exception indicating no valid GHOST candidate.
     *
     * @return GRANDPA GHOST block as a vote
     */
    public Vote getGrandpaGhost() {
        var threshold = roundState.getThreshold();

        Map<Hash256, BigInteger> blocks = getPossibleSelectedBlocks(threshold, Subround.PREVOTE);

        if (blocks.isEmpty() || threshold.equals(BigInteger.ZERO)) {
            throw new GhostExecutionException("GHOST not found");
        }

        return selectBlockWithMostVotes(blocks);
    }

    /**
     * Selects the block with the most votes from the provided map of blocks.
     * If multiple blocks have the same number of votes, it returns the one with the highest block number.
     * Starts with the last finalized block as the initial candidate.
     *
     * @param blocks map of block that exceed the required threshold
     * @return the block with the most votes from the provided map
     */
    private Vote selectBlockWithMostVotes(Map<Hash256, BigInteger> blocks) {
        Vote highest = getLastFinalizedBlockAsVote();

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
     *
     * @param threshold minimum votes required for a block to qualify.
     * @param subround  stage of the GRANDPA process, such as PREVOTE, PRECOMMIT or PRIMARY_PROPOSAL.
     * @return blocks that exceed the required vote threshold
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
     * Recursively searches for ancestors with more than 2/3 votes.
     *
     * @param votes            voters list
     * @param currentBlockHash the hash of the current block
     * @param selected         currently selected block hashes that exceed the required vote threshold
     * @param subround         stage of the GRANDPA process, such as PREVOTE, PRECOMMIT or PRIMARY_PROPOSAL.
     * @param threshold        minimum votes required for a block to qualify.
     * @return map of block hash to block number for ancestors meeting the threshold condition.
     */
    private Map<Hash256, BigInteger> getPossibleSelectedAncestors(List<Vote> votes,
                                                                  Hash256 currentBlockHash,
                                                                  Map<Hash256, BigInteger> selected,
                                                                  Subround subround,
                                                                  BigInteger threshold) {

        for (Vote vote : votes) {
            if (vote.getBlockHash().equals(currentBlockHash)) {
                continue;
            }

            Hash256 ancestorBlockHash;
            try {
                ancestorBlockHash = blockState.lowestCommonAncestor(vote.getBlockHash(), currentBlockHash);
            } catch (IllegalArgumentException | BlockStorageGenericException e) {
                log.warning("Error finding the lowest common ancestor: " + e.getMessage());
                continue;
            }

            // Happens when currentBlock is ancestor of the block in the vote
            if (ancestorBlockHash.equals(currentBlockHash)) {
                return selected;
            }

            long totalVotes = getTotalVotesForBlock(ancestorBlockHash, subround);

            if (BigInteger.valueOf(totalVotes).compareTo(threshold) >= 0) {

                BlockHeader header = blockState.getHeader(ancestorBlockHash);
                selected.put(ancestorBlockHash, header.getBlockNumber());

            } else {
                // Recursively process ancestors
                selected = getPossibleSelectedAncestors(votes, ancestorBlockHash, selected, subround, threshold);
            }
        }

        return selected;
    }

    /**
     * Calculates the total votes for a block, including observed votes and equivocations,
     * in the specified subround.
     *
     * @param blockHash hash of the block
     * @param subround  stage of the GRANDPA process, such as PREVOTE, PRECOMMIT or PRIMARY_PROPOSAL.
     * @return total votes for a specific block
     */
    private long getTotalVotesForBlock(Hash256 blockHash, Subround subround) {
        long votesForBlock = getObservedVotesForBlock(blockHash, subround);

        if (votesForBlock == 0L) {
            return 0L;
        }

        int equivocationCount = switch (subround) {
            case Subround.PREVOTE -> roundState.getPvEquivocations().size();
            case Subround.PRECOMMIT -> roundState.getPcEquivocations().size();
            default -> 0;
        };

        return votesForBlock + equivocationCount;
    }

    /**
     * Calculates the total observed votes for a block, including direct votes and votes from
     * its descendants, in the specified subround.
     *
     * @param blockHash hash of the block
     * @param subround  stage of the GRANDPA process, such as PREVOTE, PRECOMMIT or PRIMARY_PROPOSAL.
     * @return total observed votes
     */
    private long getObservedVotesForBlock(Hash256 blockHash, Subround subround) {
        var votes = getDirectVotes(subround);
        var votesForBlock = 0L;

        for (Map.Entry<Vote, Long> entry : votes.entrySet()) {
            var vote = entry.getKey();
            var count = entry.getValue();

            if (blockState.isDescendantOf(blockHash, vote.getBlockHash())) {
                votesForBlock += count;
            }
        }

        return votesForBlock;
    }

    /**
     * Aggregates direct (explicit) votes for a given subround into a map of Vote to their count
     *
     * @param subround stage of the GRANDPA process, such as PREVOTE, PRECOMMIT or PRIMARY_PROPOSAL.
     * @return map of direct votes
     */
    private HashMap<Vote, Long> getDirectVotes(Subround subround) {
        var voteCounts = new HashMap<Vote, Long>();

        Map<PubKey, Vote> votes = switch (subround) {
            case Subround.PREVOTE -> roundState.getPrevotes();
            case Subround.PRECOMMIT -> roundState.getPrecommits();
            default -> new HashMap<>();
        };

        votes.values().forEach(vote -> voteCounts.merge(vote, 1L, Long::sum));

        return voteCounts;
    }

    private List<Vote> getVotes(Subround subround) {
        var votes = getDirectVotes(subround);
        return new ArrayList<>(votes.keySet());
    }

    private Vote getLastFinalizedBlockAsVote() {
        var lastFinalizedBlockHeader = blockState.getHighestFinalizedHeader();

        return new Vote(
                lastFinalizedBlockHeader.getHash(),
                lastFinalizedBlockHeader.getBlockNumber()
        );
    }
}
