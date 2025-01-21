package com.limechain.grandpa;

import com.limechain.exception.grandpa.GhostExecutionException;
import com.limechain.exception.storage.BlockStorageGenericException;
import com.limechain.grandpa.state.GrandpaRound;
import com.limechain.grandpa.state.GrandpaSetState;
import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVote;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import com.limechain.network.protocol.grandpa.messages.vote.Subround;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.storage.block.BlockState;
import io.emeraldpay.polkaj.types.Hash256;
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

    private final GrandpaSetState grandpaSetState;
    private final BlockState blockState;

    public GrandpaService(GrandpaSetState grandpaSetState, BlockState blockState) {
        this.grandpaSetState = grandpaSetState;
        this.blockState = blockState;
    }

    private void attemptToFinalizeAt(GrandpaRound grandpaRound) {
        BigInteger lastFinalizedBlockNumber = blockState.getHighestFinalizedNumber();

        Vote bestFinalCandidate = grandpaRound.getBestFinalCandidate();
        if (bestFinalCandidate == null) {
            return;
        }

        var bestFinalCandidateVotesCount = getObservedVotesForBlock(
                grandpaRound,
                bestFinalCandidate.getBlockHash(),
                Subround.PRECOMMIT
        );

        long totalVoters = grandpaSetState.getAuthorities().size();
        long threshold = (2 * totalVoters) / 3;

        // L - last finalized block
        // E - best final candidate
        // The spec defines E >= L, but here E > L ensures E is new and not already received.
        if (bestFinalCandidate.getBlockNumber().compareTo(lastFinalizedBlockNumber) > 0 &&
                bestFinalCandidateVotesCount > threshold) {

            BlockHeader header = blockState.getHeader(bestFinalCandidate.getBlockHash());
            blockState.setFinalizedHash(header, grandpaRound.getRoundNumber(), grandpaSetState.getSetId());

            //TODO: broadcast
        }
    }

    /**
     * Determines if the specified round can be finalized.
     * 1) Checks for a valid preVote candidate and ensures it's completable.
     * 2) Retrieves the best final candidate for the current round, archives it,
     * and compares it to the previous round’s candidate.
     *
     * @return if given round is finalizable
     */
    private boolean isFinalizable(GrandpaRound grandpaRound) {

        Vote preVoteCandidate = getGrandpaGhost(grandpaRound);
        if (preVoteCandidate == null) {
            return false;
        }

        grandpaRound.setPreVotedBlock(preVoteCandidate);

        if (!isCompletable(grandpaRound)) {
            return false;
        }

        Vote bestFinalCandidate = getBestFinalCandidate(grandpaRound);
        if (bestFinalCandidate == null) {
            return false;
        }

        grandpaRound.setBestFinalCandidate(bestFinalCandidate);

        var prevGrandpaRound = grandpaRound.getPrevious();
        if (prevGrandpaRound == null) {
            return false;
        }

        Vote prevBestFinalCandidate = prevGrandpaRound.getBestFinalCandidate();

        return prevBestFinalCandidate != null
                && prevBestFinalCandidate.getBlockNumber().compareTo(bestFinalCandidate.getBlockNumber()) <= 0
                && bestFinalCandidate.getBlockNumber().compareTo(preVoteCandidate.getBlockNumber()) <= 0;
    }

    /**
     * To decide if a round is completable, we need two calculations
     * 1. [TotalPcVotes + TotalPcEquivocations > 2/3 * totalValidators]
     * 2. [TotalPcVotes - TotalPcEquivocations - (Votes where B` > Ghost) > 2/3 * totalValidators]
     * Second calculation should be done for all Ghost descendants
     *
     * @return if the current round is completable
     */
    private boolean isCompletable(GrandpaRound grandpaRound) {

        Map<Vote, Long> votes = getDirectVotes(grandpaRound, Subround.PRECOMMIT);
        long votesCount = votes.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        long equivocationsCount = grandpaRound.getPcEquivocationsCount();
        long totalVoters = grandpaSetState.getAuthorities().size();
        long threshold = (2 * totalVoters) / 3;

        if (votesCount + equivocationsCount <= threshold) {
            return false;
        }

        List<Vote> ghostDescendents = getBlockDescendents(
                grandpaRound.getPreVotedBlock(),
                new ArrayList<>(votes.keySet())
        );

        for (Vote vote : ghostDescendents) {

            var descendantBlockHash = vote.getBlockHash();
            var observedVotesForDescendantBlock = getObservedVotesForBlock(
                    grandpaRound,
                    descendantBlockHash,
                    Subround.PRECOMMIT
            );

            if (votesCount - equivocationsCount - observedVotesForDescendantBlock <= threshold) {
                return false;
            }
        }

        return true;
    }

    /**
     * Finds and returns the best final candidate block for the current round.
     * The best final candidate is determined by analyzing blocks with more than 2/3 pre-commit votes,
     * and selecting the block with the highest block number. If no such block exists, the pre-voted
     * block is returned as the best candidate.
     *
     * @return the best final candidate block
     */
    public Vote getBestFinalCandidate(GrandpaRound grandpaRound) {

        Vote preVoteCandidate = getGrandpaGhost(grandpaRound);

        if (grandpaRound.getRoundNumber().equals(BigInteger.ZERO)) {
            return preVoteCandidate;
        }

        var threshold = grandpaSetState.getThreshold();
        Map<Hash256, BigInteger> possibleSelectedBlocks = getPossibleSelectedBlocks(grandpaRound, threshold, Subround.PRECOMMIT);

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
     * Finds and returns the block with the most votes in the GRANDPA pre-vote stage.
     * If there are multiple blocks with the same number of votes, selects the block with the highest number.
     * If no block meets the criteria, throws an exception indicating no valid GHOST candidate.
     *
     * @return GRANDPA GHOST block as a vote
     */
    public Vote getGrandpaGhost(GrandpaRound grandpaRound) {
        var threshold = grandpaSetState.getThreshold();

        if (grandpaRound.getRoundNumber().equals(BigInteger.ZERO)) {
            return getLastFinalizedBlockAsVote();
        }

        Map<Hash256, BigInteger> blocks = getPossibleSelectedBlocks(grandpaRound, threshold, Subround.PREVOTE);

        if (blocks.isEmpty() || threshold.equals(BigInteger.ZERO)) {
            throw new GhostExecutionException("GHOST not found");
        }

        return selectBlockWithMostVotes(blocks);
    }

    /**
     * Determines what block is our pre-voted block for the current round
     * if we receive a vote message from the network with a
     * block that's greater than or equal to the current pre-voted block
     * and greater than the best final candidate from the last round, we choose that.
     * otherwise, we simply choose the head of our chain.
     *
     * @return the best pre-voted block
     */
    public Vote getBestPreVoteCandidate(GrandpaRound grandpaRound) {
        Vote previousBestFinalCandidate = grandpaRound.getPrevious() != null
                ? grandpaRound.getPrevious().getBestFinalCandidate()
                : new Vote(null, BigInteger.ZERO);
        Vote currentVote = getGrandpaGhost(grandpaRound);

        SignedVote primaryVote = grandpaRound.getPrimaryVote();

        if (primaryVote != null) {
            BigInteger primaryBlockNumber = primaryVote.getVote().getBlockNumber();

            if (primaryBlockNumber.compareTo(currentVote.getBlockNumber()) > 0 &&
                    primaryBlockNumber.compareTo(previousBestFinalCandidate.getBlockNumber()) > 0) {
                return primaryVote.getVote();
            }
        }
        return currentVote;
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
    private Map<Hash256, BigInteger> getPossibleSelectedBlocks(GrandpaRound grandpaRound, BigInteger threshold, Subround subround) {
        var votes = getDirectVotes(grandpaRound, subround);
        var blocks = new HashMap<Hash256, BigInteger>();

        for (Vote vote : votes.keySet()) {
            long totalVotes = getTotalVotesForBlock(grandpaRound, vote.getBlockHash(), subround);

            if (BigInteger.valueOf(totalVotes).compareTo(threshold) >= 0) {
                blocks.put(vote.getBlockHash(), vote.getBlockNumber());
            }
        }

        if (!blocks.isEmpty()) {
            return blocks;
        }

        List<Vote> allVotes = getVotes(grandpaRound, subround);
        for (Vote vote : votes.keySet()) {
            blocks = new HashMap<>(
                    getPossibleSelectedAncestors(grandpaRound, allVotes, vote.getBlockHash(), blocks, subround, threshold)
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
    private Map<Hash256, BigInteger> getPossibleSelectedAncestors(GrandpaRound grandpaRound,
                                                                  List<Vote> votes,
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

            long totalVotes = getTotalVotesForBlock(grandpaRound, ancestorBlockHash, subround);

            if (BigInteger.valueOf(totalVotes).compareTo(threshold) >= 0) {

                BlockHeader header = blockState.getHeader(ancestorBlockHash);
                selected.put(ancestorBlockHash, header.getBlockNumber());

            } else {
                // Recursively process ancestors
                selected = getPossibleSelectedAncestors(grandpaRound, votes, ancestorBlockHash, selected, subround, threshold);
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
    private long getTotalVotesForBlock(GrandpaRound grandpaRound, Hash256 blockHash, Subround subround) {
        long votesForBlock = getObservedVotesForBlock(grandpaRound, blockHash, subround);

        if (votesForBlock == 0L) {
            return 0L;
        }

        long equivocationCount = switch (subround) {
            case Subround.PREVOTE -> grandpaRound.getPvEquivocationsCount();
            case Subround.PRECOMMIT -> grandpaRound.getPcEquivocationsCount();
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
    private long getObservedVotesForBlock(GrandpaRound grandpaRound, Hash256 blockHash, Subround subround) {
        var votes = getDirectVotes(grandpaRound, subround);
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
    private HashMap<Vote, Long> getDirectVotes(GrandpaRound grandpaRound, Subround subround) {
        var voteCounts = new HashMap<Vote, Long>();

        Map<Hash256, SignedVote> votes = switch (subround) {
            case Subround.PREVOTE -> grandpaRound.getPreVotes();
            case Subround.PRECOMMIT -> grandpaRound.getPreCommits();
            default -> new HashMap<>();
        };

        votes.values().forEach(vote -> voteCounts.merge(vote.getVote(), 1L, Long::sum));

        return voteCounts;
    }

    private List<Vote> getVotes(GrandpaRound grandpaRound, Subround subround) {
        var votes = getDirectVotes(grandpaRound, subround);
        return new ArrayList<>(votes.keySet());
    }

    private List<Vote> getBlockDescendents(Vote vote, List<Vote> votes) {
        return votes.stream()
                .filter(v -> v.getBlockNumber().compareTo(vote.getBlockNumber()) > 0)
                .toList();
    }

    private Vote getLastFinalizedBlockAsVote() {
        var lastFinalizedBlockHeader = blockState.getHighestFinalizedHeader();

        return new Vote(
                lastFinalizedBlockHeader.getHash(),
                lastFinalizedBlockHeader.getBlockNumber()
        );
    }
}
