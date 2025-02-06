package com.limechain.network.protocol.grandpa;

import com.limechain.babe.api.OpaqueKeyOwnershipProof;
import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.exception.grandpa.GrandpaGenericException;
import com.limechain.network.protocol.grandpa.messages.vote.GrandpaEquivocation;
import com.limechain.exception.sync.JustificationVerificationException;
import com.limechain.grandpa.GrandpaService;
import com.limechain.grandpa.round.GrandpaRound;
import com.limechain.grandpa.round.RoundCache;
import com.limechain.grandpa.state.GrandpaSetState;
import com.limechain.grandpa.vote.SignedVote;
import com.limechain.grandpa.vote.SubRound;
import com.limechain.grandpa.vote.Vote;
import com.limechain.network.PeerMessageCoordinator;
import com.limechain.network.PeerRequester;
import com.limechain.network.protocol.grandpa.messages.catchup.req.CatchUpReqMessage;
import com.limechain.network.protocol.grandpa.messages.catchup.res.CatchUpResMessage;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import com.limechain.network.protocol.grandpa.messages.neighbour.NeighbourMessage;
import com.limechain.network.protocol.grandpa.messages.vote.FullVote;
import com.limechain.network.protocol.grandpa.messages.vote.FullVoteScaleWriter;
import com.limechain.network.protocol.grandpa.messages.vote.SignedMessage;
import com.limechain.network.protocol.grandpa.messages.vote.VoteMessage;
import com.limechain.network.protocol.sync.BlockRequestField;
import com.limechain.network.protocol.sync.pb.SyncMessage;
import com.limechain.network.protocol.warp.DigestHelper;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.Justification;
import com.limechain.network.protocol.warp.scale.reader.BlockHeaderReader;
import com.limechain.network.protocol.warp.scale.reader.JustificationReader;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.hostapi.dto.Key;
import com.limechain.runtime.hostapi.dto.VerifySignature;
import com.limechain.state.AbstractState;
import com.limechain.state.StateManager;
import com.limechain.storage.block.state.BlockState;
import com.limechain.sync.JustificationVerifier;
import com.limechain.sync.state.SyncState;
import com.limechain.sync.warpsync.WarpSyncState;
import com.limechain.utils.Ed25519Utils;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.types.Hash256;
import io.emeraldpay.polkaj.types.Hash256;
import io.libp2p.core.PeerId;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.limechain.grandpa.vote.SubRound.PRE_COMMIT;

@Log
@RequiredArgsConstructor
@Component
public class GrandpaMessageHandler {
    private static final BigInteger CATCH_UP_THRESHOLD = BigInteger.TWO;

    private final StateManager stateManager;
    private final GrandpaService grandpaService;
    private final PeerMessageCoordinator messageCoordinator;
    private final WarpSyncState warpSyncState;
    private final PeerRequester requester;

    /**
     * Handles a vote message, extracts signed vote, associates it with the correct round.
     * Depending on the subround type, the vote is added to pre-votes, pre-commits, or marked as the primary proposal.
     *
     * @param voteMessage received vote message.
     */
    public void handleVoteMessage(VoteMessage voteMessage) {
        RoundCache roundCache = stateManager.getGrandpaSetState().getRoundCache();
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

        SubRound subround = signedMessage.getStage();

        if (isVoteEquivocationExist(signedVote, round, subround, voteMessageSetId)) {
            log.warning(
                    String.format("Reported vote equivocation for round %s, set %s, block hash %s, block number %s",
                            voteMessageSetId, voteMessageSetId, signedMessage.getBlockHash(), signedMessage.getBlockNumber()));
            return;
        }

        switch (subround) {
            case PRE_VOTE -> round.getPreVotes().put(signedMessage.getAuthorityPublicKey(), signedVote);
            case PRE_COMMIT -> round.getPreCommits().put(signedMessage.getAuthorityPublicKey(), signedVote);
            case PRIMARY_PROPOSAL -> {
                round.setPrimaryVote(signedVote.getVote());
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

    private boolean isVoteEquivocationExist(SignedVote signedVote, GrandpaRound round, SubRound subRound, BigInteger voteMessageSetId) {
        Map<Hash256, SignedVote> votes = PRE_COMMIT.equals(subRound) ? round.getPreCommits() : round.getPreVotes();
        Map<Hash256, Set<SignedVote>> equivocations = PRE_COMMIT.equals(subRound) ? round.getPcEquivocations() : round.getPvEquivocations();

        Hash256 authorityPublicKey = signedVote.getAuthorityPublicKey();

        if (votes.containsKey(authorityPublicKey)) {
            equivocations.computeIfAbsent(authorityPublicKey, _ -> new HashSet<>()).add(signedVote);
            Runtime runtime = warpSyncState.getRuntime();
            SignedVote firstSignedVote = votes.get(authorityPublicKey);

            GrandpaEquivocation grandpaEquivocation = new GrandpaEquivocation();
            grandpaEquivocation.setSetId(voteMessageSetId);
            grandpaEquivocation.setEquivocationStage((byte) (PRE_COMMIT.equals(subRound) ? 1 : 0));
            grandpaEquivocation.setRoundNumber(round.getRoundNumber());
            grandpaEquivocation.setFirstBlockNumber(firstSignedVote.getVote().getBlockNumber());
            grandpaEquivocation.setFirstBlockHash(firstSignedVote.getVote().getBlockHash());
            grandpaEquivocation.setFirstSignature(firstSignedVote.getSignature());
            grandpaEquivocation.setSecondBlockNumber(signedVote.getVote().getBlockNumber());
            grandpaEquivocation.setSecondBlockHash(signedVote.getVote().getBlockHash());
            grandpaEquivocation.setSecondSignature(signedVote.getSignature());

            Optional<OpaqueKeyOwnershipProof> opaqueKeyOwnershipProof = runtime.generateGrandpaKeyOwnershipProof(voteMessageSetId, authorityPublicKey.getBytes());
            opaqueKeyOwnershipProof.ifPresentOrElse(
                    key -> runtime.submitReportGrandpaEquivocationUnsignedExtrinsic(grandpaEquivocation, key.getProof()),
                    () -> log.warning(String.format(
                            "Failure to report Grandpa vote equivocation for authority: %s.",
                            authorityPublicKey)));
            return true;
        }

        return false;
    }

    /**
     * Updates the Host's state with information from a commit message.
     * Synchronized to avoid race condition between checking and updating latest block
     * Scheduled runtime updates for synchronized blocks are executed.
     *
     * @param commitMessage received commit message
     * @param peerId        sender of the message
     */
    public synchronized void handleCommitMessage(CommitMessage commitMessage, PeerId peerId) {
        if (commitMessage.getVote().getBlockNumber().compareTo(
                stateManager.getSyncState().getLastFinalizedBlockNumber()) <= 0) {
            log.log(Level.FINE, String.format("Received commit message for finalized block %d from peer %s",
                    commitMessage.getVote().getBlockNumber(), peerId));
            return;
        }

        log.log(Level.FINE, "Received commit message from peer " + peerId
                + " for block #" + commitMessage.getVote().getBlockNumber()
                + " with hash " + commitMessage.getVote().getBlockHash()
                + " with setId " + commitMessage.getSetId() + " and round " + commitMessage.getRoundNumber()
                + " with " + commitMessage.getPreCommits().length + " voters");

        boolean verified = JustificationVerifier.verify(Justification.fromCommitMessage(commitMessage));
        if (!verified) {
            log.log(Level.WARNING, "Could not verify commit from peer: " + peerId);
            return;
        }

        GrandpaSetState grandpaSetState = stateManager.getGrandpaSetState();
        Optional.ofNullable(grandpaSetState.getRoundCache()
                        .getRound(commitMessage.getSetId(), commitMessage.getRoundNumber()))
                .ifPresent(round -> round.addCommitMessageToArchive(commitMessage));

        if (warpSyncState.isWarpSyncFinished() && !AbstractState.isActiveAuthority()) {
            updateSyncStateAndRuntime(commitMessage);
        }
    }

    /**
     * Updates the Host's state with information from a neighbour message.
     * Tries to update Host's set data (id and authorities) if neighbour has a greater set id than the Host.
     * Synchronized to avoid race condition between checking and updating set id
     *
     * @param neighbourMessage received neighbour message
     * @param peerId           sender of message
     */
    public void handleNeighbourMessage(NeighbourMessage neighbourMessage, PeerId peerId) {
        messageCoordinator.sendNeighbourMessageToPeer(peerId);
        if (warpSyncState.isWarpSyncFinished() && neighbourMessage.getSetId()
                .compareTo(stateManager.getGrandpaSetState().getSetId()) > 0) {
            BigInteger setChangeBlock = neighbourMessage.getLastFinalizedBlock().add(BigInteger.ONE);

            List<SyncMessage.BlockData> response = requester.requestBlockData(
                    BlockRequestField.ALL,
                    setChangeBlock.intValueExact(),
                    1
            ).join();

            SyncMessage.BlockData block = response.getFirst();

            if (block.getIsEmptyJustification()) {
                log.log(Level.WARNING, "No justification for block " + setChangeBlock);
                return;
            }

            Justification justification = ScaleUtils.Decode.decode(
                    block.getJustification().toByteArray(), JustificationReader.getInstance());

            boolean verified = JustificationVerifier.verify(justification);

            if (verified) {
                processNeighbourUpdates(block);
            }
        }
    }

    /**
     * Initiates and sends a catch-up request to a specific peer.
     *
     * @param neighbourMessage received neighbour message
     * @param peerId           peer to send the catch-up message to
     */
    public void initiateAndSendCatchUpRequest(NeighbourMessage neighbourMessage, PeerId peerId) {
        GrandpaSetState grandpaSetState = stateManager.getGrandpaSetState();
        // If peer has the same voter set id
        if (neighbourMessage.getSetId().equals(grandpaSetState.getSetId())) {

            // Check if needed to catch-up peer
            if (neighbourMessage.getRound().compareTo(
                    grandpaSetState.fetchLatestRoundNumber().add(CATCH_UP_THRESHOLD)) >= 0) {
                log.log(Level.FINE, "Neighbor message indicates that the round of Peer " + peerId + " is ahead.");

                CatchUpReqMessage catchUpReqMessage = CatchUpReqMessage.builder()
                        .round(neighbourMessage.getRound())
                        .setId(neighbourMessage.getSetId()).build();

                messageCoordinator.sendCatchUpRequestToPeer(peerId, catchUpReqMessage);
            }
        }
    }

    /**
     * Handles a catch-up request from a peer, initiating and sending corresponding catch-up response.
     *
     * @param peerId            peer requesting catch-up message
     * @param catchUpReqMessage received catch-up request message
     * @param peerIds           set of connected peer ids
     */
    public void initiateAndSendCatchUpResponse(PeerId peerId,
                                               CatchUpReqMessage catchUpReqMessage,
                                               Supplier<Set<PeerId>> peerIds) {

        GrandpaSetState grandpaSetState = stateManager.getGrandpaSetState();
        if (!peerIds.get().contains(peerId)) {
            throw new GrandpaGenericException("Requesting catching up from a non-peer.");
        }

        if (!catchUpReqMessage.getSetId().equals(grandpaSetState.getSetId())) {
            throw new GrandpaGenericException("Catch up message has a different setId.");
        }

        if (catchUpReqMessage.getRound().compareTo(grandpaSetState.fetchLatestRoundNumber()) > 0) {
            throw new GrandpaGenericException("Catching up on a round in the future.");
        }

        GrandpaRound selectedGrandpaRound = selectRound(catchUpReqMessage.getRound(), catchUpReqMessage.getSetId())
                .orElseThrow(() -> new GrandpaGenericException("Target round was no found."));

        SignedVote[] preVotes = getPreVoteJustification(selectedGrandpaRound);
        SignedVote[] preCommits = getPreCommitJustification(selectedGrandpaRound);

        BlockHeader finalizedBlockHeader = selectedGrandpaRound.getFinalizedBlock();

        CatchUpResMessage catchUpResMessage = CatchUpResMessage.builder()
                .round(selectedGrandpaRound.getRoundNumber())
                .setId(grandpaSetState.getSetId())
                .preCommits(preCommits)
                .preVotes(preVotes)
                .blockHash(finalizedBlockHeader.getHash())
                .blockNumber(finalizedBlockHeader.getBlockNumber())
                .build();

        messageCoordinator.sendCatchUpResponseToPeer(peerId, catchUpResMessage);
    }

    /**
     * Handles a catch-up response from a peer, validating pre-votes and pre-commits.
     * Initializes a new round, determines the Grandpa Ghost and finalization estimate,
     * checks round compatibility, and executes the Play-Grandpa-Round if valid.
     *
     * @param peerId            peer responding with catch-up message
     * @param catchUpResMessage received catch-up response message
     * @param peerIds           set of connected peer ids
     */
    public void handleCatchUpResponse(PeerId peerId,
                                      CatchUpResMessage catchUpResMessage,
                                      Supplier<Set<PeerId>> peerIds) {

        GrandpaSetState grandpaSetState = stateManager.getGrandpaSetState();
        if (!peerIds.get().contains(peerId)) {
            throw new GrandpaGenericException("Receiving catching up response from a non-peer.");
        }

        if (!catchUpResMessage.getSetId().equals(grandpaSetState.getSetId())) {
            throw new GrandpaGenericException("Catch up response has a different setId.");
        }

        RoundCache roundCache = grandpaSetState.getRoundCache();
        GrandpaRound latestRound = roundCache.getLatestRound(grandpaSetState.getSetId());
        if (catchUpResMessage.getRound().compareTo(latestRound.getRoundNumber()) <= 0) {
            throw new GrandpaGenericException("Catching up into a round in the past.");
        }

        BlockState blockState = stateManager.getBlockState();
        BlockHeader finalizedTarget = blockState.getHeaderByNumber(catchUpResMessage.getBlockNumber());
        if (!finalizedTarget.getHash().equals(catchUpResMessage.getBlockHash())) {
            throw new GrandpaGenericException("Catch up response with non-matching block hash and block number.");
        }

        GrandpaRound grandpaRound = new GrandpaRound(
                null,
                catchUpResMessage.getRound(),
                false,
                grandpaSetState.getThreshold(),
                latestRound.getLastFinalizedBlock()
        );
        //Todo: Maybe we should set previous block, as it is needed in the current implementation of findGhost
        grandpaRound.setFinalizedBlock(finalizedTarget);
        setPreVotesAndPvEquivocations(grandpaRound, catchUpResMessage.getPreVotes());
        setPreCommitsAndPcEquivocations(grandpaRound, catchUpResMessage.getPreCommits());

        boolean verified = JustificationVerifier.verify(Justification.fromCatchUpResMessage(catchUpResMessage));
        if (!verified) {
            throw new JustificationVerificationException("Justification could not be verified.");
        }

        BlockHeader bestFinalCandidate = grandpaRound.getBestFinalCandidate();
        if (!bestFinalCandidate.getHash().equals(finalizedTarget.getHash())) {
            throw new GrandpaGenericException("Unjustified Catch-up target finalization");
        }

        //Todo: Iterate over preVotes, for each check if we are at Stage::PRE_COMMIT_WAITS_FOR_PRE_VOTES
        //      then updateGrandpaGhost if we have obtained enough preVotes. If grandpaGhost is updated,
        //      finish the PRE_COMMIT_WAITS_FOR_PRE_VOTES stage.

        //Todo: If preVotes and preCommits are valid, we updateGrandpaGhost, updateFinalizeEstimate
        //      and attemptToFinalizeRound

        //Todo: Play grandpa round for the currently created round
    }

    private void setPreVotesAndPvEquivocations(GrandpaRound grandpaRound, SignedVote[] votes) {
        setVotesAndEquivocations(grandpaRound, votes, GrandpaRound::setPreVotes, GrandpaRound::setPvEquivocations);
    }

    private void setPreCommitsAndPcEquivocations(GrandpaRound grandpaRound, SignedVote[] votes) {
        setVotesAndEquivocations(grandpaRound, votes, GrandpaRound::setPreCommits, GrandpaRound::setPcEquivocations);
    }

    private void setVotesAndEquivocations(GrandpaRound grandpaRound,
                                          SignedVote[] votes,
                                          BiConsumer<GrandpaRound, Map<Hash256, SignedVote>> setUniqueVotes,
                                          BiConsumer<GrandpaRound, Map<Hash256, List<SignedVote>>> setEquivocations) {

        // Group votes by AuthorityPublicKey
        Map<Hash256, List<SignedVote>> voteCount = Arrays.stream(votes)
                .collect(Collectors.groupingBy(SignedVote::getAuthorityPublicKey));

        Map<Hash256, SignedVote> uniqueVotes = new ConcurrentHashMap<>();
        Map<Hash256, List<SignedVote>> equivocations = new ConcurrentHashMap<>();

        for (Map.Entry<Hash256, List<SignedVote>> entry : voteCount.entrySet()) {
            List<SignedVote> voteList = entry.getValue();
            Hash256 authorityKey = entry.getKey();

            if (voteList.size() == 1) {
                uniqueVotes.put(authorityKey, voteList.getFirst());
            } else {
                equivocations.put(authorityKey, voteList);
            }
        }

        setUniqueVotes.accept(grandpaRound, uniqueVotes);
        setEquivocations.accept(grandpaRound, equivocations);
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

    private void updateSyncStateAndRuntime(CommitMessage commitMessage) {
        SyncState syncState = stateManager.getSyncState();
        BigInteger lastFinalizedBlockNumber = syncState.getLastFinalizedBlockNumber();
        if (commitMessage.getVote().getBlockNumber().compareTo(lastFinalizedBlockNumber) <= 0) {
            return;
        }
        syncState.finalizedCommitMessage(commitMessage);

        new Thread(() -> warpSyncState.updateRuntime(lastFinalizedBlockNumber)).start();
    }

    private void processNeighbourUpdates(SyncMessage.BlockData block) {
        BlockHeader header = ScaleUtils.Decode.decode(
                block.getHeader().toByteArray(), BlockHeaderReader.getInstance());

        stateManager.getSyncState().finalizeHeader(header);

        DigestHelper.getGrandpaConsensusMessage(header.getDigest())
                .ifPresent(cm -> stateManager.getGrandpaSetState()
                        .handleGrandpaConsensusMessage(cm, header.getBlockNumber()));

        // Executes scheduled or forced authority changes for the last finalized block.
        boolean changeInAuthoritySet = stateManager.getGrandpaSetState().handleAuthoritySetChange(
                stateManager.getSyncState().getLastFinalizedBlockNumber());

        if (warpSyncState.isWarpSyncFinished() && changeInAuthoritySet) {
            new Thread(messageCoordinator::sendMessagesToPeers).start();
        }
    }

    private Optional<GrandpaRound> selectRound(BigInteger peerRoundNumber, BigInteger peerSetId) {
        GrandpaSetState grandpaSetState = stateManager.getGrandpaSetState();

        GrandpaRound round = grandpaSetState.getRoundCache().getLatestRound(grandpaSetState.getSetId());

        while (round != null) {
            // Round found
            // Check voter set
            if (round.getRoundNumber().equals(peerRoundNumber)) {
                if (round.getCommitMessagesArchive().getFirst().getSetId().equals(peerSetId)) {
                    break;
                }
            }
            // Go to the previous round
            round = round.getPrevious();
        }

        return Optional.ofNullable(round);
    }

    private SignedVote[] getPreVoteJustification(GrandpaRound requestedRound) {
        BlockHeader estimate = requestedRound.getBestFinalCandidate();
        BlockState blockState = stateManager.getBlockState();
        Hash256 estimateHash = estimate.getHash();

        Predicate<SignedVote> isDescendant = vote ->
                blockState.isDescendantOf(estimateHash, vote.getVote().getBlockHash());

        return Stream.concat(
                        requestedRound.getPreVotes().values().stream(),
                        requestedRound.getPvEquivocations().values().stream().flatMap(List::stream)
                )
                .filter(isDescendant)
                .toArray(SignedVote[]::new);
    }

    private SignedVote[] getPreCommitJustification(GrandpaRound requestedRound) {
        BlockHeader finalizedBlock = requestedRound.getFinalizedBlock();
        BigInteger totalWeight = BigInteger.ZERO;
        BigInteger threshold = stateManager.getGrandpaSetState().getThreshold();

        List<SignedVote> result = new ArrayList<>();

        Stream<SignedVote> allPreCommits = Stream.concat(
                requestedRound.getPcEquivocations().values().stream().flatMap(List::stream),
                requestedRound.getPreCommits().values().stream()
        );

        for (SignedVote vote : allPreCommits.toList()) {
            if (totalWeight.compareTo(threshold) >= 0) break;
            totalWeight = increaseWeightAndAddVote(vote, finalizedBlock, totalWeight, result);
        }

        return result.toArray(SignedVote[]::new);
    }

    private BigInteger increaseWeightAndAddVote(SignedVote vote,
                                                BlockHeader finalizedBlock,
                                                BigInteger totalWeight,
                                                List<SignedVote> result) {

        BlockState blockState = stateManager.getBlockState();
        if (finalizedBlock.getBlockNumber().compareTo(vote.getVote().getBlockNumber()) <= 0 &&
                blockState.isDescendantOf(finalizedBlock.getHash(), vote.getVote().getBlockHash())) {

            BigInteger voterWeight = getAuthorityWeight(vote.getAuthorityPublicKey()).orElse(BigInteger.ZERO);
            if (voterWeight.compareTo(BigInteger.ZERO) > 0) {
                totalWeight = totalWeight.add(voterWeight);
                result.add(vote);
            }
        }
        return totalWeight;
    }

    private Optional<BigInteger> getAuthorityWeight(Hash256 authorityPublicKey) {
        return stateManager.getGrandpaSetState().getAuthorities().stream()
                .filter(authority -> new Hash256(authority.getPublicKey()).equals(authorityPublicKey))
                .map(Authority::getWeight)
                .findFirst();
    }
}
