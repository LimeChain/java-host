package com.limechain.grandpa;

import com.limechain.exception.grandpa.GhostExecutionException;
import com.limechain.grandpa.state.GrandpaRound;
import com.limechain.grandpa.state.GrandpaSetState;
import com.limechain.network.PeerMessageCoordinator;
import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVote;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import com.limechain.network.protocol.grandpa.messages.vote.SignedMessage;
import com.limechain.network.protocol.grandpa.messages.vote.Subround;
import com.limechain.network.protocol.grandpa.messages.vote.VoteMessage;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.ConsensusEngine;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.network.protocol.warp.dto.PreCommit;
import com.limechain.storage.block.BlockState;
import io.emeraldpay.polkaj.types.Hash256;
import io.emeraldpay.polkaj.types.Hash512;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.limechain.utils.TestUtils.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Disabled
class GrandpaServiceTest {

    private static final byte[] ZEROS_ARRAY = new byte[32];
    private static final byte[] ONES_ARRAY =
            new byte[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    private static final byte[] TWOS_ARRAY =
            new byte[]{2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};
    private static final byte[] THREES_ARRAY =
            new byte[]{3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3};

    private GrandpaSetState grandpaSetState;
    private BlockState blockState;
    private GrandpaService grandpaService;
    private GrandpaRound grandpaRound;
    private PeerMessageCoordinator peerMessageCoordinator;

    @BeforeEach
    void setUp() {
        grandpaSetState = mock(GrandpaSetState.class);
        blockState = mock(BlockState.class);
        peerMessageCoordinator = mock(PeerMessageCoordinator.class);
        grandpaService = new GrandpaService(grandpaSetState, peerMessageCoordinator);
        grandpaRound = mock(GrandpaRound.class);
    }

    @Test
    void testGetBestFinalCandidateWithoutPreCommits() {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));

        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);

        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of());
        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                firstVoteAuthorityHash, firstSignedVote,
                secondVoteAuthorityHash, secondSignedVote
        ));

        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);
        when(blockState.isDescendantOf(firstVote.getBlockHash(), firstVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(secondVote.getBlockHash(), secondVote.getBlockHash())).thenReturn(true);

        Vote result = grandpaService.getBestFinalCandidate(grandpaRound);

        assertNotNull(result);
        assertEquals(firstVote.getBlockHash(), result.getBlockHash());
    }

    @Test
    void testGetBestFinalCandidateWithPreCommitBlockNumberBiggerThatPreVoteBlockNumber() {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(4));
        Vote thirdVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(5));

        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        Hash256 thirdVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);
        SignedVote thirdSignedVote = new SignedVote(thirdVote, Hash512.empty(), thirdVoteAuthorityHash);

        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                firstVoteAuthorityHash, firstSignedVote,
                secondVoteAuthorityHash, secondSignedVote
        ));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of(
                thirdVoteAuthorityHash, thirdSignedVote
        ));

        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);
        when(blockState.isDescendantOf(firstVote.getBlockHash(), firstVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(secondVote.getBlockHash(), secondVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(thirdVote.getBlockHash(), thirdVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(thirdVote.getBlockHash(), firstVote.getBlockHash())).thenReturn(true);

        Vote result = grandpaService.getBestFinalCandidate(grandpaRound);

        assertNotNull(result);
        assertEquals(thirdVote.getBlockHash(), result.getBlockHash());
    }

    @Test
    void testGetBestFinalCandidateWithPreCommitBlockNumberLessThatPreVoteBlockNumber() {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(4));
        Vote thirdVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(5));

        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        Hash256 thirdVoteAuthorityHash = new Hash256(ONES_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);
        SignedVote thirdSignedVote = new SignedVote(thirdVote, Hash512.empty(), thirdVoteAuthorityHash);

        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(6));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                firstVoteAuthorityHash, firstSignedVote,
                secondVoteAuthorityHash, secondSignedVote
        ));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of(
                thirdVoteAuthorityHash, thirdSignedVote
        ));

        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);
        when(blockState.isDescendantOf(firstVote.getBlockHash(), firstVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(secondVote.getBlockHash(), secondVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(thirdVote.getBlockHash(), thirdVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(thirdVote.getBlockHash(), firstVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(thirdVote.getBlockHash(), blockHeader.getHash())).thenReturn(true);

        Vote result = grandpaService.getBestFinalCandidate(grandpaRound);

        assertNotNull(result);
        assertEquals(blockHeader.getHash(), result.getBlockHash());
    }

    @Test
    void testGetBestFinalCandidateWhereRoundNumberIsZero() {
        BlockHeader blockHeader = createBlockHeader();

        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(0));
        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);

        var result = grandpaService.getBestFinalCandidate(grandpaRound);
        assertEquals(blockHeader.getHash(), result.getBlockHash());
        assertEquals(blockHeader.getBlockNumber(), result.getBlockNumber());
    }

    @Test
    void testGetBestPreVoteCandidate_WithSignedMessage() {
        Vote currentVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Hash256 currentVoteAuthorityHash = new Hash256(ONES_ARRAY);
        SignedVote currentSignedVote = new SignedVote(currentVote, Hash512.empty(), currentVoteAuthorityHash);
        Vote primaryVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));
        Hash256 primaryVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote primarySignedVote = new SignedVote(primaryVote, Hash512.empty(), primaryVoteAuthorityHash);
        VoteMessage voteMessage = mock(VoteMessage.class);
        SignedMessage signedMessage = mock(SignedMessage.class);

        when(grandpaRound.getPrimaryVote()).thenReturn(primarySignedVote);
        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of());
        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                currentVoteAuthorityHash, currentSignedVote
        ));
        GrandpaRound previousRound = new GrandpaRound();
        previousRound.setBestFinalCandidate(new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(2)));
        when(grandpaRound.getPrevious()).thenReturn(previousRound);

        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);
        when(blockState.isDescendantOf(currentVote.getBlockHash(), currentVote.getBlockHash())).thenReturn(true);
        when(voteMessage.getMessage()).thenReturn(signedMessage);
        when(signedMessage.getBlockNumber()).thenReturn(BigInteger.valueOf(4));
        when(signedMessage.getBlockHash()).thenReturn(new Hash256(TWOS_ARRAY));

        Vote result = grandpaService.getBestPreVoteCandidate(grandpaRound);


        assertNotNull(result);
        assertEquals(new Hash256(TWOS_ARRAY), result.getBlockHash());
        assertEquals(BigInteger.valueOf(4), result.getBlockNumber());
    }

    @Test
    void testGetBestPreVoteCandidate_WithoutSignedMessage() {
        Vote currentVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Hash256 currentVoteAuthorityHash = new Hash256(ONES_ARRAY);
        SignedVote currentSignedVote = new SignedVote(currentVote, Hash512.empty(), currentVoteAuthorityHash);
        Vote primaryVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Hash256 primaryVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote primarySignedVote = new SignedVote(primaryVote, Hash512.empty(), primaryVoteAuthorityHash);

        when(grandpaRound.getPrimaryVote()).thenReturn(primarySignedVote);
        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of());
        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                currentVoteAuthorityHash, currentSignedVote
        ));

        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);
        when(blockState.isDescendantOf(currentVote.getBlockHash(), currentVote.getBlockHash())).thenReturn(true);

        Vote result = grandpaService.getBestPreVoteCandidate(grandpaRound);

        assertNotNull(result);
        assertEquals(currentVote.getBlockHash(), result.getBlockHash());
        assertEquals(currentVote.getBlockNumber(), result.getBlockNumber());
    }

    @Test
    void testGetBestPreVoteCandidate_WithSignedMessageAndBlockNumberLessThanCurrentVote() {
        Vote currentVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(4));
        Hash256 currentVoteAuthorityHash = new Hash256(ONES_ARRAY);
        SignedVote currentSignedVote = new SignedVote(currentVote, Hash512.empty(), currentVoteAuthorityHash);
        Vote primaryVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Hash256 primaryVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote primarySignedVote = new SignedVote(primaryVote, Hash512.empty(), primaryVoteAuthorityHash);
        SignedMessage signedMessage = mock(SignedMessage.class);

        when(grandpaRound.getPrimaryVote()).thenReturn(primarySignedVote);
        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of());
        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                currentVoteAuthorityHash, currentSignedVote
        ));

        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);
        when(blockState.isDescendantOf(currentVote.getBlockHash(), currentVote.getBlockHash())).thenReturn(true);
        when(signedMessage.getBlockNumber()).thenReturn(BigInteger.valueOf(3));
        when(signedMessage.getBlockHash()).thenReturn(new Hash256(TWOS_ARRAY));

        Vote result = grandpaService.getBestPreVoteCandidate(grandpaRound);

        assertNotNull(result);
        assertEquals(currentVote.getBlockHash(), result.getBlockHash());
        assertEquals(currentVote.getBlockNumber(), result.getBlockNumber());
    }

    @Test
    void testGetGrandpaGHOSTWhereNoBlocksPassThreshold() {
        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(10));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getPreVotes()).thenReturn(Map.of());
        assertThrows(GhostExecutionException.class, () -> grandpaService.getGrandpaGhost(grandpaRound));
    }

    @Test
    void testGetGrandpaGHOSTWhereRoundNumberIsZero() {
        BlockHeader blockHeader = createBlockHeader();

        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(0));
        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);

        var result = grandpaService.getGrandpaGhost(grandpaRound);
        assertEquals(blockHeader.getHash(), result.getBlockHash());
        assertEquals(blockHeader.getBlockNumber(), result.getBlockNumber());
    }

    @Test
    void testGetGrandpaGHOSTWithBlockPassingThreshold() {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));

        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);

        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                firstVoteAuthorityHash, firstSignedVote,
                secondVoteAuthorityHash, secondSignedVote
        ));

        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);
        when(blockState.isDescendantOf(firstVote.getBlockHash(), firstVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(secondVote.getBlockHash(), secondVote.getBlockHash())).thenReturn(true);

        Vote result = grandpaService.getGrandpaGhost(grandpaRound);
        assertNotNull(result);
        assertEquals(firstVote.getBlockHash(), result.getBlockHash());
    }

    @Test
    void testGetDirectVotesForPreVotes() throws Exception {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));
        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);

        Map<Hash256, SignedVote> preVotes = new HashMap<>();
        preVotes.put(firstVoteAuthorityHash, firstSignedVote);
        preVotes.put(secondVoteAuthorityHash, secondSignedVote);

        when(grandpaRound.getPreVotes()).thenReturn(preVotes);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("getDirectVotes", GrandpaRound.class, Subround.class);
        method.setAccessible(true);

        Map<Vote, Long> result = (HashMap<Vote, Long>) method.invoke(grandpaService, grandpaRound, Subround.PREVOTE);

        assertEquals(1L, result.get(firstVote));
        assertEquals(1L, result.get(secondVote));
    }

    @Test
    void testGetDirectVotesWithMultipleVotesForSingleBlockForPreVotes() throws Exception {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);

        Map<Hash256, SignedVote> preVotes = new HashMap<>();

        preVotes.put(firstVoteAuthorityHash, firstSignedVote);
        preVotes.put(secondVoteAuthorityHash, secondSignedVote);

        when(grandpaRound.getPreVotes()).thenReturn(preVotes);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("getDirectVotes", GrandpaRound.class, Subround.class);
        method.setAccessible(true);

        Map<Vote, Long> result = (HashMap<Vote, Long>) method.invoke(grandpaService, grandpaRound, Subround.PREVOTE);

        assertEquals(2L, result.get(firstVote));
    }

    @Test
    void testGetVotes() throws Exception {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);

        Map<Hash256, SignedVote> preVotes = new HashMap<>();

        preVotes.put(firstVoteAuthorityHash, firstSignedVote);

        when(grandpaRound.getPreVotes()).thenReturn(preVotes);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("getVotes", GrandpaRound.class, Subround.class);
        method.setAccessible(true);

        List<Vote> result = (List<Vote>) method.invoke(grandpaService, grandpaRound, Subround.PREVOTE);

        assertTrue(result.contains(firstVote));
    }

    @Test
    void testGetVotesWithMultipleVotes() throws Exception {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));
        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);

        Map<Hash256, SignedVote> preVotes = new HashMap<>();
        preVotes.put(firstVoteAuthorityHash, firstSignedVote);
        preVotes.put(secondVoteAuthorityHash, secondSignedVote);

        when(grandpaRound.getPreVotes()).thenReturn(preVotes);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("getVotes", GrandpaRound.class, Subround.class);
        method.setAccessible(true);

        List<Vote> result = (List<Vote>) method.invoke(grandpaService, grandpaRound, Subround.PREVOTE);

        assertTrue(result.contains(firstVote));
        assertTrue(result.contains(secondVote));
    }

    @Test
    void testGetObservedVotesForBlockWhereVotesAreNotDescendantsOfProvidedBlockHash() throws Exception {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));
        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);

        Map<Hash256, SignedVote> preVotes = new HashMap<>();
        preVotes.put(firstVoteAuthorityHash, firstSignedVote);
        preVotes.put(secondVoteAuthorityHash, secondSignedVote);

        when(grandpaRound.getPreVotes()).thenReturn(preVotes);
        when(blockState.isDescendantOf(any(), any())).thenReturn(false);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getObservedVotesForBlock",
                GrandpaRound.class,
                Hash256.class,
                Subround.class
        );

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, grandpaRound, new Hash256(ZEROS_ARRAY), Subround.PREVOTE);

        assertEquals(0L, result);
    }

    @Test
    void testGetObservedVotesForBlockWhereVotesAreDescendantsOfProvidedBlockHash() throws Exception {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));
        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);

        Map<Hash256, SignedVote> preVotes = new HashMap<>();
        preVotes.put(firstVoteAuthorityHash, firstSignedVote);
        preVotes.put(secondVoteAuthorityHash, secondSignedVote);

        when(grandpaRound.getPreVotes()).thenReturn(preVotes);
        when(blockState.isDescendantOf(any(), any())).thenReturn(true);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getObservedVotesForBlock",
                GrandpaRound.class,
                Hash256.class,
                Subround.class
        );

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, grandpaRound, new Hash256(ZEROS_ARRAY), Subround.PREVOTE);

        assertEquals(2L, result);
    }

    @Test
    void testGetTotalVotesForBlockWithoutObservedVotes() throws Exception {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));

        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);

        Map<Hash256, SignedVote> preVotes = new HashMap<>();
        preVotes.put(firstVoteAuthorityHash, firstSignedVote);
        preVotes.put(secondVoteAuthorityHash, secondSignedVote);

        Hash256 thirdVoteAuthorityHash = new Hash256(ONES_ARRAY);

        Map<Hash256, Set<SignedVote>> pvEquivocations = new HashMap<>();
        pvEquivocations.put(thirdVoteAuthorityHash, Set.of(new SignedVote()));

        when(grandpaRound.getPreVotes()).thenReturn(preVotes);
        when(grandpaRound.getPvEquivocations()).thenReturn(pvEquivocations);
        when(blockState.isDescendantOf(any(), any())).thenReturn(false);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getTotalVotesForBlock", GrandpaRound.class, Hash256.class, Subround.class);

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, grandpaRound, new Hash256(ZEROS_ARRAY), Subround.PREVOTE);

        // Observed votes: 0
        // Equivocations: 1
        // Total votes: 0
        assertEquals(0, result);
    }

    @Test
    void testGetTotalVotesForBlockWithObservedVotes() throws Exception {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));

        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);

        Map<Hash256, SignedVote> preVotes = new HashMap<>();
        preVotes.put(firstVoteAuthorityHash, firstSignedVote);
        preVotes.put(secondVoteAuthorityHash, secondSignedVote);

        when(grandpaRound.getPreVotes()).thenReturn(preVotes);
        when(grandpaRound.getPvEquivocations()).thenReturn(new HashMap<>());
        when(blockState.isDescendantOf(any(), any())).thenReturn(true);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getTotalVotesForBlock", GrandpaRound.class, Hash256.class, Subround.class);

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, grandpaRound, new Hash256(ZEROS_ARRAY), Subround.PREVOTE);

        // Observed votes: 2
        // Equivocations: 0
        // Total votes: 2
        assertEquals(2, result);
    }

    @Test
    void testGetTotalVotesForBlockWithObservedVotesAndEquivocations() throws Exception {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));

        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);

        Map<Hash256, SignedVote> preVotes = new HashMap<>();
        preVotes.put(firstVoteAuthorityHash, firstSignedVote);
        preVotes.put(secondVoteAuthorityHash, secondSignedVote);

        when(grandpaRound.getPreVotes()).thenReturn(preVotes);
        when(grandpaRound.getPvEquivocationsCount()).thenReturn(1L);
        when(blockState.isDescendantOf(any(), any())).thenReturn(true);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getTotalVotesForBlock", GrandpaRound.class, Hash256.class, Subround.class);

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, grandpaRound, new Hash256(ZEROS_ARRAY), Subround.PREVOTE);

        // Observed votes: 2
        // Equivocations: 1
        // Total votes: 3
        assertEquals(3, result);
    }

    @Test
    void testGetPossibleSelectedAncestors() throws Exception {
        // ZEROS_ARRAY Block is parent of ONES- and THREES_ARRAY Blocks
        //
        // ZEROS_ARRAY_BLOCK --> ONES_ARRAY_BLOCK (block from votes)
        //  |
        //  --> THREES_ARRAY_BLOCK (current block)
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        List<Vote> votes = List.of(firstVote);

        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);

        Map<Hash256, SignedVote> preVotes = new HashMap<>();
        preVotes.put(firstVoteAuthorityHash, firstSignedVote);

        when(grandpaRound.getPreVotes()).thenReturn(preVotes);
        when(grandpaRound.getPvEquivocations()).thenReturn(new HashMap<>());
        when(blockState.isDescendantOf(any(), any())).thenReturn(true);

        when(blockState.lowestCommonAncestor(new Hash256(ONES_ARRAY), new Hash256(THREES_ARRAY)))
                .thenReturn(new Hash256(ZEROS_ARRAY));

        when(blockState.getHeader(new Hash256(ZEROS_ARRAY)))
                .thenReturn(createBlockHeader());

        Method method = GrandpaService.class.getDeclaredMethod("getPossibleSelectedAncestors",
                GrandpaRound.class,
                List.class,
                Hash256.class,
                Map.class,
                Subround.class,
                BigInteger.class
        );

        method.setAccessible(true);

        Map<Hash256, BigInteger> selected = new HashMap<>();

        Map<Hash256, BigInteger> result = (Map<Hash256, BigInteger>) method.invoke(
                grandpaService,
                grandpaRound,
                votes,
                new Hash256(THREES_ARRAY),
                selected,
                Subround.PREVOTE,
                BigInteger.valueOf(1)
        );

        assertEquals(1, result.size());
        assertTrue(result.containsKey(new Hash256(ZEROS_ARRAY)));
    }


    @Test
    void testGetPossibleSelectedBlocksThatAreOverThreshold() throws Exception {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));

        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                firstVoteAuthorityHash, firstSignedVote,
                secondVoteAuthorityHash, secondSignedVote
        ));

        when(blockState.isDescendantOf(any(), any())).thenReturn(true);
        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(3));

        Method method = GrandpaService.class.getDeclaredMethod(
                "getPossibleSelectedBlocks", GrandpaRound.class, BigInteger.class, Subround.class);
        method.setAccessible(true);

        Map<Hash256, BigInteger> result = (Map<Hash256, BigInteger>) method.invoke(
                grandpaService, grandpaRound, BigInteger.valueOf(1), Subround.PREVOTE);

        assertEquals(2, result.size());
        assertTrue(result.containsKey(new Hash256(ONES_ARRAY)));
        assertTrue(result.containsKey(new Hash256(TWOS_ARRAY)));
    }

    @Test
    void testSelectBlockWithMostVotes() throws Exception {
        Map<Hash256, BigInteger> blocks = new HashMap<>();
        blocks.put(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        blocks.put(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));

        BlockHeader blockHeader = createBlockHeader();
        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);

        Method method = GrandpaService.class.getDeclaredMethod("selectBlockWithMostVotes", Map.class);
        method.setAccessible(true);

        Vote result = (Vote) method.invoke(grandpaService, blocks);

        assertNotNull(result);
        assertEquals(new Hash256(TWOS_ARRAY), result.getBlockHash());
        assertEquals(BigInteger.valueOf(4), result.getBlockNumber());
    }

    @Test
    void testSelectBlockWithMostVotesWhereLastFinalizedBlockIsWithGreaterBlockNumber() throws Exception {
        Map<Hash256, BigInteger> blocks = new HashMap<>();
        blocks.put(new Hash256(ONES_ARRAY), BigInteger.valueOf(0));

        BlockHeader blockHeader = createBlockHeader();
        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);

        Method method = GrandpaService.class.getDeclaredMethod("selectBlockWithMostVotes", Map.class);
        method.setAccessible(true);

        Vote result = (Vote) method.invoke(grandpaService, blocks);

        assertNotNull(result);
        assertEquals(blockHeader.getHash(), result.getBlockHash());
        assertEquals(blockHeader.getBlockNumber(), result.getBlockNumber());
    }

    @Test
    void testPrimaryBroadcastCommitMessage() {
        Hash256 authorityPublicKey = new Hash256(THREES_ARRAY);
        Map<Hash256, SignedVote> signedVotes = new HashMap<>();
        Vote vote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(123L));
        SignedVote signedVote = new SignedVote(vote, Hash512.empty(), authorityPublicKey);
        signedVotes.put(authorityPublicKey, signedVote);

        GrandpaRound previousRound = new GrandpaRound();
        previousRound.setRoundNumber(BigInteger.ZERO);
        previousRound.setPreCommits(signedVotes);
        BlockHeader blockHeader = createBlockHeader();

        when(grandpaRound.getPrevious()).thenReturn(previousRound);
        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.ONE);
        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);
        when(grandpaSetState.getSetId()).thenReturn(BigInteger.valueOf(42L));

        grandpaService.broadcastCommitMessage(grandpaRound);

        ArgumentCaptor<CommitMessage> commitMessageCaptor = ArgumentCaptor.forClass(CommitMessage.class);
        verify(peerMessageCoordinator).sendCommitMessageToPeers(commitMessageCaptor.capture());
        CommitMessage commitMessage = commitMessageCaptor.getValue();

        assertEquals(BigInteger.valueOf(42L), commitMessage.getSetId());
        assertEquals(BigInteger.valueOf(0L), commitMessage.getRoundNumber());
        assertEquals(blockHeader.getBlockNumber(), commitMessage.getVote().getBlockNumber());
        assertEquals(blockHeader.getHash(), commitMessage.getVote().getBlockHash());
        assertEquals(1, commitMessage.getPreCommits().length);

        PreCommit precommit = commitMessage.getPreCommits()[0];
        assertEquals(vote.getBlockHash(), precommit.getTargetHash());
        assertEquals(BigInteger.valueOf(123L), precommit.getTargetNumber());
        assertEquals(Hash512.empty(), precommit.getSignature());
        assertEquals(signedVotes.get(authorityPublicKey).getAuthorityPublicKey(),
                precommit.getAuthorityPublicKey()
        );

        verify(peerMessageCoordinator, times(1))
                .sendCommitMessageToPeers(any(CommitMessage.class));
    }

    private BlockHeader createBlockHeader() {
        HeaderDigest headerDigest = new HeaderDigest();
        headerDigest.setType(DigestType.CONSENSUS_MESSAGE);
        headerDigest.setId(ConsensusEngine.GRANDPA);
        headerDigest.setMessage(ZEROS_ARRAY);

        BlockHeader blockHeader = new BlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));
        blockHeader.setParentHash(new Hash256(ZEROS_ARRAY));
        blockHeader.setDigest(new HeaderDigest[]{headerDigest});
        blockHeader.setStateRoot(new Hash256(ZEROS_ARRAY));
        blockHeader.setExtrinsicsRoot(new Hash256(ZEROS_ARRAY));

        return blockHeader;
    }
}
