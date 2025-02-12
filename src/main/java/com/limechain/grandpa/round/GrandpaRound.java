package com.limechain.grandpa.round;

import com.limechain.exception.grandpa.EstimateExecutionException;
import com.limechain.exception.grandpa.GhostExecutionException;
import com.limechain.exception.grandpa.GrandpaGenericException;
import com.limechain.exception.storage.BlockStorageGenericException;
import com.limechain.grandpa.state.GrandpaSetState;
import com.limechain.grandpa.vote.SignedVote;
import com.limechain.grandpa.vote.SubRound;
import com.limechain.grandpa.vote.Vote;
import com.limechain.network.PeerMessageCoordinator;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import com.limechain.network.protocol.grandpa.messages.vote.FullVote;
import com.limechain.network.protocol.grandpa.messages.vote.FullVoteScaleWriter;
import com.limechain.network.protocol.grandpa.messages.vote.SignedMessage;
import com.limechain.network.protocol.grandpa.messages.vote.VoteMessage;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.rpc.server.AppBean;
import com.limechain.state.AbstractState;
import com.limechain.state.StateManager;
import com.limechain.storage.block.state.BlockState;
import com.limechain.utils.Ed25519Utils;
import com.limechain.utils.async.AsyncExecutor;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.types.Hash256;
import io.emeraldpay.polkaj.types.Hash512;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.java.Log;
import org.javatuples.Pair;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

@Log
@Getter
@Setter
@RequiredArgsConstructor
public class GrandpaRound {

    // Based on https://github.com/paritytech/polkadot/pull/6217
    public static final long DURATION = 1000;

    private final GrandpaRound previous;
    private final BigInteger roundNumber;

    private final boolean isPrimaryVoter;
    private final BigInteger threshold;

    private StageState stage = new StartStage();

    /**
     * Current finalized block at the start of the round.
     */
    private final BlockHeader lastFinalizedBlock;

    /**
     * Invoked when the round attempts to finalize a block with >= 2/3 + 1 pre commits.
     */
    private Runnable onFinalizeHandler;
    /**
     * Serves as a timer when {@link PreVoteStage} and {@link PreCommitStage} have to wait for incoming votes.
     */
    private ScheduledExecutorService onStageTimerHandler;

    private Instant startTime = null;

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
     * Null before {@link PreVoteStage} finishes.
     */
    @Nullable
    private Vote preVoteChoice;
    /**
     * Best final candidate.<BR>
     * Null before {@link PreCommitStage} finishes.
     */
    @Nullable
    private Vote preCommitChoice;

    /**
     * Updated when the Grandpa Ghost is calculated.<BR>
     * Default value is false.
     */
    private boolean isCompletable;

    private Map<Hash256, SignedVote> preVotes = new ConcurrentHashMap<>();
    private Map<Hash256, SignedVote> preCommits = new ConcurrentHashMap<>();
    private Vote primaryVote;

    private Map<Hash256, List<SignedVote>> pvEquivocations = new ConcurrentHashMap<>();
    private Map<Hash256, List<SignedVote>> pcEquivocations = new ConcurrentHashMap<>();

    private final List<CommitMessage> commitMessagesArchive = new ArrayList<>();

    private final StateManager stateManager = Objects.requireNonNull(AppBean.getBean(StateManager.class));
    private final PeerMessageCoordinator peerMessageCoordinator = Objects.requireNonNull(
            AppBean.getBean(PeerMessageCoordinator.class));

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

    public long getPvEquivocationsWeight() {
        return this.pvEquivocations.size();
    }

    public long getPcEquivocationsWeight() {
        return this.pcEquivocations.size();
    }

    public void play() {
        stage.start(this);
    }

    public void complete() {
        stage = new CompletedStage();
        stage.start(this);
    }

    public void addCommitMessageToArchive(CommitMessage message) {
        commitMessagesArchive.add(message);
    }

    public boolean isCommitMessageInArchive(Vote vote) {
        return commitMessagesArchive.stream()
                .anyMatch(cm -> cm.getVote().equals(vote));
    }

    public BlockHeader getPrevBestFinalCandidate() {
        if (previous != null) {
            return previous.getBestFinalCandidate();
        }

        return lastFinalizedBlock;
    }

    public BlockHeader getBestFinalCandidate() {
        return Optional.ofNullable(finalizeEstimate).orElse(lastFinalizedBlock);
    }

    public void attemptToFinalize() {

        BlockState blockState = stateManager.getBlockState();

        if (stage instanceof CompletedStage) {
            log.fine("attemptToFinalize: round is already complete.");
            return;
        }

        if (finalizedBlock != null) {
            GrandpaSetState grandpaSetState = stateManager.getGrandpaSetState();

            blockState.setFinalizedHash(finalizedBlock, roundNumber, grandpaSetState.getSetId());

            // TODO save only votes here.
            grandpaSetState.persistState();

            if (!isCommitMessageInArchive(Vote.fromBlockHeader(finalizedBlock))) {
                broadcastCommitMessage();
            }

            if (onFinalizeHandler != null) {
                onFinalizeHandler.run();
            }
        }
    }

    private boolean updateEstimate() {

        BigInteger pvWeight = getVoteWeight(preVotes.values());
        if (pvWeight.compareTo(threshold) < 0) {
            log.fine("updateEstimate: pre vote weight is lower than threshold");
            return false;
        }

        if (grandpaGhost == null) {
            log.fine("updateEstimate: grandpa ghost is null");
            return false;
        }
        final BlockHeader pv_ghost = grandpaGhost;

        BigInteger pcWeight = getVoteWeight(preCommits.values());
        if (pcWeight.compareTo(threshold) >= 0) {
            finalizedBlock = findBestFinalCandidate(weight -> weight.compareTo(threshold) >= 0,
                    SubRound.PRE_COMMIT,
                    pv_ghost);
        }

        if (pcWeight.compareTo(threshold) < 0) {
            finalizeEstimate = pv_ghost;
            log.fine("updateEstimate: pre commit weight is lower than threshold");
            return false;
        }


        try {
            Function<BigInteger, Boolean> condition = getPotentialCondition();

            finalizeEstimate = findBestFinalCandidate(condition, SubRound.PRE_COMMIT, pv_ghost);
            BlockHeader current_estimate = finalizeEstimate;

            if (!current_estimate.getHash().equals(pv_ghost.getHash())) {
                isCompletable = true;
                log.fine("updateEstimate: estimate != ghost");
            } else {
                checkPotentialGhost(condition, current_estimate);
            }
        } catch (EstimateExecutionException e) {
            log.fine("updateEstimate: " + e.getMessage());
        }

        return true;
    }

    private void checkPotentialGhost(Function<BigInteger, Boolean> condition, BlockHeader estimate) {

        try {
            BlockHeader pc_ghost = findBestFinalCandidate(condition, SubRound.PRE_COMMIT, estimate);

            if (pc_ghost.getHash().equals(estimate.getHash())) {
                isCompletable = true;
                log.fine("updateEstimate: estimate == pc_ghost");
            } else {
                log.fine("updateEstimate: estimate != pc_ghost");
            }
        } catch (EstimateExecutionException e) {
            isCompletable = true;
            log.fine("updateEstimate: no pc_ghost");
        }
    }

    /**
     * Finds and returns the best final candidate block for the current round.
     * The best final candidate is determined by analyzing blocks with more than 2/3 pre-commit votes,
     * and selecting the block with the highest block number. If no such block exists, the pre-voted
     * block is returned as the best candidate.
     *
     * @param condition a boolean expression that serves the purpose of a customizable threshold.
     * @param round     the subround that we check votes against a condition for.
     * @return the best final candidate block
     */
    private BlockHeader findBestFinalCandidate(Function<BigInteger, Boolean> condition,
                                               SubRound round,
                                               BlockHeader borderBlock) {
        BlockState blockState = stateManager.getBlockState();

        if (roundNumber.equals(BigInteger.ZERO)) {
            return grandpaGhost;
        }

        Map<Hash256, BigInteger> possibleSelectedBlocks = getPossibleSelectedBlocks(
                condition,
                round
        );

        if (possibleSelectedBlocks.isEmpty()) {
            throw new EstimateExecutionException("Estimate not found.");
        }

        var bestFinalCandidate = lastFinalizedBlock;

        for (Map.Entry<Hash256, BigInteger> block : possibleSelectedBlocks.entrySet()) {

            Hash256 blockHash = block.getKey();

            boolean isDescendant = blockState.isDescendantOf(blockHash, borderBlock.getHash());

            if (!isDescendant) {

                Hash256 lowestCommonAncestor;
                try {
                    lowestCommonAncestor = blockState.lowestCommonAncestor(blockHash, borderBlock.getHash());
                } catch (IllegalArgumentException e) {
                    log.warning("Error finding the lowest common ancestor: " + e.getMessage());
                    continue;
                }

                blockHash = lowestCommonAncestor;
            }

            BlockHeader header = BlockHeader.fromHash(blockHash);
            if (header.getBlockNumber().compareTo(bestFinalCandidate.getBlockNumber()) > 0) {
                bestFinalCandidate = header;
            }
        }

        return bestFinalCandidate;
    }

    private Function<BigInteger, Boolean> getPotentialCondition() {

        GrandpaSetState grandpaSetState = stateManager.getGrandpaSetState();

        BigInteger totalAuthWeight = grandpaSetState.getAuthoritiesTotalWeight();
        BigInteger totalPcWeight = getVoteWeight(preCommits.values());

        // Calculate how many more pre commit equivocations we are allowed to receive.
        BigInteger totalAllowedEqs = totalAuthWeight.subtract(threshold);
        BigInteger currentEqs = BigInteger.valueOf(getPcEquivocationsWeight());
        BigInteger remainingAllowedEqs = totalAllowedEqs.subtract(currentEqs);

        // Calculate how many more pre commits we can expect.
        BigInteger remainingPcs = totalAuthWeight.subtract(totalPcWeight);

        return totalWeight -> {

            BigInteger possibleEqsForBlock = totalPcWeight.subtract(totalWeight)
                    .min(remainingAllowedEqs);

            BigInteger potentialWeightForBlock = totalWeight
                    .add(remainingPcs)
                    .add(possibleEqsForBlock);

            return potentialWeightForBlock.compareTo(threshold) >= 0;
        };
    }

    public void update(boolean isPrevRoundChanged, boolean isPreVoteChanged, boolean isPreCommitChanged) {

        boolean shouldUpdateGhost = isPrevRoundChanged || isPreVoteChanged;
        boolean shouldUpdateEstimate = isPreCommitChanged;

        if (shouldUpdateGhost) {
            shouldUpdateEstimate = updateGrandpaGhost();

            if (grandpaGhost != null) {
                AsyncExecutor.withSingleThread().executeAndForget(() -> {
                    if (stage instanceof PreCommitStage) {
                        stage.end(this);
                    }
                });
            }
        }

        if (shouldUpdateEstimate) {
            if (updateEstimate()) {
                attemptToFinalize();

                //TODO update R + 1
            }
        }

        //TODO check if we can start next round.
    }

    private boolean updateGrandpaGhost() {
        BigInteger preVotesWeight = getVoteWeight(preVotes.values());

        if (preVotesWeight.compareTo(threshold) < 0) {
            log.fine("updateGrandpaGhost: pre vote weight is lower than threshold");
            return false;
        }

        BlockHeader newGrandpaGhost;
        try {
            newGrandpaGhost = findGrandpaGhost(weight -> weight.compareTo(threshold) >= 0, SubRound.PRE_VOTE);
        } catch (RuntimeException e) {
            log.warning("updateGrandpaGhost: error when updating grandpa ghost: " + e.getMessage());
            return false;
        }

        boolean changed = grandpaGhost == null ||
                !newGrandpaGhost.getHash().equals(grandpaGhost.getHash());
        grandpaGhost = newGrandpaGhost;

        if (changed) {
            log.fine(String.format("updateGrandpaGhost: round: %d updated ghost to %s.",
                    roundNumber, newGrandpaGhost.getHash()));
        } else {
            log.fine(String.format("updateGrandpaGhost: round: %d did not update ghost.", roundNumber));
        }

        return changed;
    }

    /**
     * Finds and returns the block with the most votes in the GRANDPA pre-vote stage.
     * If there are multiple blocks with the same number of votes, selects the block with the highest number.
     * If no block meets the criteria, throws an exception indicating no valid GHOST candidate.
     *
     * @return GRANDPA GHOST block as a vote
     */
    public BlockHeader findGrandpaGhost(Function<BigInteger, Boolean> condition, SubRound subround) {

        if (roundNumber.equals(BigInteger.ZERO)) {
            return lastFinalizedBlock;
        }

        Map<Hash256, BigInteger> blocks = getPossibleSelectedBlocks(condition, subround);

        if (blocks.isEmpty() || threshold.equals(BigInteger.ZERO)) {
            throw new GhostExecutionException("GHOST not found");
        }

        return selectBlockWithMostVotes(blocks, getPrevBestFinalCandidate());
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
    public Vote findBestPreVoteCandidate() {

        BlockHeader choiceHeader = getGrandpaGhost();

        if (primaryVote != null) {
            BigInteger primaryBlockNumber = primaryVote.getBlockNumber();
            BlockHeader previousBestFinalCandidate = getPrevBestFinalCandidate();

            if (primaryBlockNumber.compareTo(choiceHeader.getBlockNumber()) > 0 &&
                    primaryBlockNumber.compareTo(previousBestFinalCandidate.getBlockNumber()) > 0) {
                return primaryVote;
            }
        }

        Vote choiceVote = Vote.fromBlockHeader(choiceHeader);
        preVoteChoice = choiceVote;

        return choiceVote;
    }

    /**
     * Selects the block with the most votes from the provided map of blocks.
     * If multiple blocks have the same number of votes, it returns the one with the highest block number.
     * Starts with the last finalized block as the initial candidate.
     *
     * @param blocks map of block that exceed the required threshold
     * @return the block with the most votes from the provided map
     */
    private BlockHeader selectBlockWithMostVotes(Map<Hash256, BigInteger> blocks, BlockHeader start) {

        BlockHeader highest = start;

        for (Map.Entry<Hash256, BigInteger> entry : blocks.entrySet()) {
            Hash256 currentHash = entry.getKey();
            BigInteger number = entry.getValue();

            if (number.compareTo(highest.getBlockNumber()) > 0) {
                highest = BlockHeader.fromHash(currentHash);
            }
        }

        return highest;
    }

    /**
     * Returns blocks with total votes over the threshold in a map of block hash to block number.
     * If no blocks meet the threshold directly, recursively searches their ancestors for blocks with enough votes.
     * Ancestors are included if their combined votes (including votes for their descendants) exceed the threshold.
     *
     * @param condition a boolean expression that serves the purpose of a customizable threshold.
     * @param subround  stage of the GRANDPA process, such as {@link SubRound#PRE_VOTE},
     *                  {@link SubRound#PRE_COMMIT} or {@link SubRound#PRIMARY_PROPOSAL}.
     * @return blocks that exceed the required vote threshold
     */
    private Map<Hash256, BigInteger> getPossibleSelectedBlocks(Function<BigInteger, Boolean> condition,
                                                               SubRound subround) {

        //TODO We can reverse order this by block num.
        HashMap<Vote, Long> votes = getDirectVotes(subround);

        var blocks = new HashMap<Hash256, BigInteger>();

        for (Vote vote : votes.keySet()) {
            long totalVotes = getTotalVotesForBlock(vote.getBlockHash(), subround);

            if (condition.apply(BigInteger.valueOf(totalVotes))) {
                blocks.put(vote.getBlockHash(), vote.getBlockNumber());
            }
        }

        if (!blocks.isEmpty()) {
            return blocks;
        }

        // List of all unique votes. (probably one per voted block)
        List<Vote> allVotes = getVotes(subround);
        for (Vote vote : votes.keySet()) {
            blocks = new HashMap<>(
                    getPossibleSelectedAncestors(allVotes,
                            vote.getBlockHash(),
                            blocks,
                            subround,
                            condition
                    )
            );
        }

        return blocks;
    }

    /**
     * Recursively searches for ancestors with more than 2/3 votes.
     *
     * @param votes            List of all unique votes. (probably one per voted block)
     * @param currentBlockHash hash of a block that has a vote in this round.
     * @param selected         currently selected block hashes that exceed the required vote threshold (FOR SOME REASON THIS IS REASSIGNED IN METHOD)
     * @param subround         stage of the GRANDPA process, such as PREVOTE, PRECOMMIT or PRIMARY_PROPOSAL.
     * @param condition        a boolean expression that serves the purpose of a customizable threshold.
     * @return map of block hash to block number for ancestors meeting the threshold condition.
     */
    private Map<Hash256, BigInteger> getPossibleSelectedAncestors(List<Vote> votes,
                                                                  Hash256 currentBlockHash,
                                                                  Map<Hash256, BigInteger> selected,
                                                                  SubRound subround,
                                                                  Function<BigInteger, Boolean> condition) {

        BlockState blockState = stateManager.getBlockState();
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

            if (condition.apply(BigInteger.valueOf(totalVotes))) {

                BlockHeader header = blockState.getHeader(ancestorBlockHash);
                selected.put(ancestorBlockHash, header.getBlockNumber());

            } else {
                // Recursively process ancestors
                selected = getPossibleSelectedAncestors(votes,
                        ancestorBlockHash,
                        selected,
                        subround,
                        condition
                );
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
    private long getTotalVotesForBlock(Hash256 blockHash, SubRound subround) {
        long votesForBlock = getObservedVotesForBlock(blockHash, subround);

        if (votesForBlock == 0L) {
            return 0L;
        }

        //TODO check if we have to retrieve only weight of equivocator (1) or actual number of eq votes.
        long equivocationCount = switch (subround) {
            case SubRound.PRE_VOTE -> getPvEquivocationsWeight();
            case SubRound.PRE_COMMIT -> getPcEquivocationsWeight();
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
    private long getObservedVotesForBlock(Hash256 blockHash, SubRound subround) {
        //TODO we can use BlockState.getAllDescendants() and limit to only
        // descendants instead of checking on each iteration.
        HashMap<Vote, Long> votes = getDirectVotes(subround);
        long votesForBlock = 0L;

        for (Map.Entry<Vote, Long> entry : votes.entrySet()) {
            Vote vote = entry.getKey();
            Long count = entry.getValue();

            if (stateManager.getBlockState().isDescendantOf(blockHash, vote.getBlockHash())) {
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
    private HashMap<Vote, Long> getDirectVotes(SubRound subround) {
        var voteCounts = new HashMap<Vote, Long>();

        Map<Hash256, SignedVote> votes = switch (subround) {
            case SubRound.PRE_VOTE -> getPreVotes();
            case SubRound.PRE_COMMIT -> getPreCommits();
            default -> new HashMap<>();
        };

        votes.values().forEach(vote -> voteCounts.merge(vote.getVote(), 1L, Long::sum));

        return voteCounts;
    }

    private List<Vote> getVotes(SubRound subround) {
        HashMap<Vote, Long> votes = getDirectVotes(subround);
        return new ArrayList<>(votes.keySet());
    }

    /**
     * Broadcasts a vote message to network peers for the given round as part of the GRANDPA consensus process.
     * <p>
     * This method is used when broadcasting a vote message for the best preVote candidate block of the current round.
     */
    public void broadcastVoteMessage(Vote vote, SubRound subround) {
        FullVote fullVote = new FullVote();
        fullVote.setRound(roundNumber);
        fullVote.setSetId(stateManager.getGrandpaSetState().getSetId());
        fullVote.setVote(vote);
        fullVote.setStage(subround);

        byte[] encodedFullVote = ScaleUtils.Encode.encode(FullVoteScaleWriter.getInstance(), fullVote);

        SignedMessage signedMessage = new SignedMessage();
        signedMessage.setStage(fullVote.getStage());
        signedMessage.setBlockNumber(vote.getBlockNumber());
        signedMessage.setBlockHash(vote.getBlockHash());

        Pair<byte[], byte[]> grandpaKeyPair = AbstractState.getGrandpaKeyPair();

        byte[] pubKey = grandpaKeyPair.getValue0();
        byte[] privateKey = grandpaKeyPair.getValue1();
        byte[] signature = Ed25519Utils.signMessage(privateKey, encodedFullVote);
        signedMessage.setAuthorityPublicKey(new Hash256(pubKey));
        signedMessage.setSignature(new Hash512(signature));

        VoteMessage voteMessage = new VoteMessage();
        voteMessage.setRound(fullVote.getRound());
        voteMessage.setSetId(fullVote.getSetId());
        voteMessage.setMessage(signedMessage);

        peerMessageCoordinator.sendVoteMessageToPeers(voteMessage);
    }

    /**
     * Broadcasts a commit message to network peers for the given round as part of the GRANDPA consensus process.
     * <p>
     * This method is used in two scenarios:
     * 1. As the primary validator, broadcasting a commit message for the best candidate block of the previous round.
     * 2. During attempt-to-finalize, broadcasting a commit message for the best candidate block of the current round.
     */
    public void broadcastCommitMessage() {
        SignedVote[] preCommits = getPreCommits().values().toArray(new SignedVote[0]);

        CommitMessage commitMessage = new CommitMessage();
        commitMessage.setSetId(stateManager.getGrandpaSetState().getSetId());
        commitMessage.setRoundNumber(roundNumber);
        commitMessage.setVote(Vote.fromBlockHeader(getBestFinalCandidate()));
        commitMessage.setPreCommits(preCommits);

        peerMessageCoordinator.sendCommitMessageToPeers(commitMessage);
    }

    public void clearOnStageTimerHandler() {
        if (onStageTimerHandler != null && onStageTimerHandler.isShutdown()) {
            onStageTimerHandler.shutdown();
            onStageTimerHandler = null;
        }
    }

    private BigInteger getVoteWeight(Collection<SignedVote> votes) {
        BigInteger voteWeight = BigInteger.ZERO;

        for (SignedVote vote : votes) {
            Optional<BigInteger> authorityWeight = stateManager.getGrandpaSetState()
                    .getAuthorityWeight(vote.getAuthorityPublicKey());
            voteWeight = voteWeight.add(authorityWeight.orElse(BigInteger.ZERO));
        }
        return voteWeight;
    }
}
