package com.limechain.grandpa;

import com.limechain.exception.grandpa.GhostExecutionException;
import com.limechain.grandpa.state.GrandpaRound;
import com.limechain.grandpa.state.GrandpaSetState;
import com.limechain.network.protocol.grandpa.messages.catchup.res.SignedVote;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import com.limechain.network.protocol.grandpa.messages.vote.SignedMessage;
import com.limechain.network.protocol.grandpa.messages.vote.Subround;
import com.limechain.network.protocol.grandpa.messages.vote.VoteMessage;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.ConsensusEngine;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.storage.block.BlockState;
import com.limechain.utils.Ed25519Utils;
import io.emeraldpay.polkaj.types.Hash256;
import io.libp2p.core.crypto.PubKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.limechain.utils.TestUtils.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @BeforeEach
    void setUp() {
        grandpaSetState = mock(GrandpaSetState.class);
        blockState = mock(BlockState.class);
        grandpaService = new GrandpaService(grandpaSetState, blockState);
        grandpaRound = new GrandpaRound();
    }

    @Test
    void testGetBestFinalCandidateWithoutPreCommits() {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));

        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of());
        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                Ed25519Utils.generateKeyPair().publicKey(), firstVote,
                Ed25519Utils.generateKeyPair().publicKey(), secondVote
        ));

        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);
        when(blockState.isDescendantOf(firstVote.getBlockHash(), firstVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(secondVote.getBlockHash(), secondVote.getBlockHash())).thenReturn(true);

        Vote result = grandpaService.getBestFinalCandidate();

        assertNotNull(result);
        assertEquals(firstVote.getBlockHash(), result.getBlockHash());
    }

    @Test
    void testGetBestFinalCandidateWithPreCommitBlockNumberBiggerThatPreVoteBlockNumber() {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(4));
        Vote thirdVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(5));

        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                Ed25519Utils.generateKeyPair().publicKey(), firstVote,
                Ed25519Utils.generateKeyPair().publicKey(), secondVote
        ));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of(
                Ed25519Utils.generateKeyPair().publicKey(), thirdVote
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

        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(6));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                Ed25519Utils.generateKeyPair().publicKey(), firstVote,
                Ed25519Utils.generateKeyPair().publicKey(), secondVote
        ));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of(
                Ed25519Utils.generateKeyPair().publicKey(), thirdVote
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
        VoteMessage voteMessage = mock(VoteMessage.class);
        SignedMessage signedMessage = mock(SignedMessage.class);

//        when(grandpaSetState.getVoteMessage()).thenReturn(voteMessage);
        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of());
        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                Ed25519Utils.generateKeyPair().publicKey(), currentVote
        ));

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
        VoteMessage voteMessage = mock(VoteMessage.class);

//        when(grandpaSetState.getVoteMessage()).thenReturn(voteMessage);
        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of());
        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                Ed25519Utils.generateKeyPair().publicKey(), currentVote
        ));

        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);
        when(blockState.isDescendantOf(currentVote.getBlockHash(), currentVote.getBlockHash())).thenReturn(true);

        Vote result = grandpaService.getBestPreVoteCandidate();

        assertNotNull(result);
        assertEquals(currentVote.getBlockHash(), result.getBlockHash());
        assertEquals(currentVote.getBlockNumber(), result.getBlockNumber());
    }

    @Test
    void testGetBestPreVoteCandidate_WithSignedMessageAndBlockNumberLessThanCurrentVote() {
        Vote currentVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(4));
        VoteMessage voteMessage = mock(VoteMessage.class);
        SignedMessage signedMessage = mock(SignedMessage.class);

      //  when(grandpaSetState.getVoteMessage()).thenReturn(voteMessage);
        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of());
        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                Ed25519Utils.generateKeyPair().publicKey(), currentVote
        ));

        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);
        when(blockState.isDescendantOf(currentVote.getBlockHash(), currentVote.getBlockHash())).thenReturn(true);
        when(voteMessage.getMessage()).thenReturn(signedMessage);
        when(signedMessage.getBlockNumber()).thenReturn(BigInteger.valueOf(3));
        when(signedMessage.getBlockHash()).thenReturn(new Hash256(TWOS_ARRAY));

        Vote result = grandpaService.getBestPreVoteCandidate();

        assertNotNull(result);
        assertEquals(currentVote.getBlockHash(), result.getBlockHash());
        assertEquals(currentVote.getBlockNumber(), result.getBlockNumber());
    }

    @Test
    void testGetGrandpaGHOSTWhereNoBlocksPassThreshold() {
        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(10));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getPreVotes()).thenReturn(Map.of());
        assertThrows(GhostExecutionException.class, () -> grandpaService.getGrandpaGhost());
    }

    @Test
    void testGetGrandpaGHOSTWhereRoundNumberIsZero() {
        BlockHeader blockHeader = createBlockHeader();

        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(0));
        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);

        var result = grandpaService.getGrandpaGhost();
        assertEquals(blockHeader.getHash(), result.getBlockHash());
        assertEquals(blockHeader.getBlockNumber(), result.getBlockNumber());
    }

    @Test
    void testGetGrandpaGHOSTWithBlockPassingThreshold() {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));

        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                Ed25519Utils.generateKeyPair().publicKey(), firstVote,
                Ed25519Utils.generateKeyPair().publicKey(), secondVote
        ));

        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);
        when(blockState.isDescendantOf(firstVote.getBlockHash(), firstVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(secondVote.getBlockHash(), secondVote.getBlockHash())).thenReturn(true);

        Vote result = grandpaService.getGrandpaGhost();
        assertNotNull(result);
        assertEquals(firstVote.getBlockHash(), result.getBlockHash());
    }

    @Test
    void testGetDirectVotesForPrevotes() throws Exception {
        // Prepare mock data
        PubKey pubKey1 = Ed25519Utils.generateKeyPair().publicKey();
        PubKey pubKey2 = Ed25519Utils.generateKeyPair().publicKey();

        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));

        Map<PubKey, Vote> prevotes = new HashMap<>();
        prevotes.put(pubKey1, firstVote);
        prevotes.put(pubKey2, secondVote);

        when(grandpaRound.getPreVotes()).thenReturn(prevotes);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("getDirectVotes", Subround.class);
        method.setAccessible(true);

        Map<Vote, Long> result = (HashMap<Vote, Long>) method.invoke(grandpaService, Subround.PREVOTE);

        assertEquals(1L, result.get(firstVote));
        assertEquals(1L, result.get(secondVote));
    }

    @Test
    void testGetDirectVotesWithMultipleVotesForSingleBlockForPrevotes() throws Exception {
        // Prepare mock data
        PubKey pubKey1 = Ed25519Utils.generateKeyPair().publicKey();
        PubKey pubKey2 = Ed25519Utils.generateKeyPair().publicKey();

        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));

        Map<PubKey, Vote> prevotes = new HashMap<>();
        prevotes.put(pubKey1, firstVote);
        prevotes.put(pubKey2, secondVote);

        when(grandpaRound.getPreVotes()).thenReturn(prevotes);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("getDirectVotes", Subround.class);
        method.setAccessible(true);

        Map<Vote, Long> result = (HashMap<Vote, Long>) method.invoke(grandpaService, Subround.PREVOTE);

        assertEquals(2L, result.get(firstVote));
    }

    @Test
    void testGetVotes() throws Exception {
        // Prepare mock data
        PubKey pubKey1 = Ed25519Utils.generateKeyPair().publicKey();

        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));

        Map<PubKey, Vote> prevotes = new HashMap<>();
        prevotes.put(pubKey1, firstVote);

        when(grandpaRound.getPreVotes()).thenReturn(prevotes);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("getVotes", Subround.class);
        method.setAccessible(true);

        List<Vote> result = (List<Vote>) method.invoke(grandpaService, Subround.PREVOTE);

        assertTrue(result.contains(firstVote));
    }

    @Test
    void testGetVotesWithMultipleVotes() throws Exception {
        // Prepare mock data
        PubKey pubKey1 = Ed25519Utils.generateKeyPair().publicKey();
        PubKey pubKey2 = Ed25519Utils.generateKeyPair().publicKey();

        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));

        Map<PubKey, Vote> prevotes = new HashMap<>();
        prevotes.put(pubKey1, firstVote);
        prevotes.put(pubKey2, secondVote);

        when(grandpaRound.getPreVotes()).thenReturn(prevotes);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("getVotes", Subround.class);
        method.setAccessible(true);

        List<Vote> result = (List<Vote>) method.invoke(grandpaService, Subround.PREVOTE);

        assertTrue(result.contains(firstVote));
        assertTrue(result.contains(secondVote));
    }

    @Test
    void testGetObservedVotesForBlockWhereVotesAreNotDescendantsOfProvidedBlockHash() throws Exception {
        PubKey pubKey1 = Ed25519Utils.generateKeyPair().publicKey();
        PubKey pubKey2 = Ed25519Utils.generateKeyPair().publicKey();

        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));

        Map<PubKey, Vote> prevotes = new HashMap<>();
        prevotes.put(pubKey1, firstVote);
        prevotes.put(pubKey2, secondVote);

        when(grandpaRound.getPreVotes()).thenReturn(prevotes);
        when(blockState.isDescendantOf(any(), any())).thenReturn(false);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getObservedVotesForBlock",
                Hash256.class,
                Subround.class
        );

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, new Hash256(ZEROS_ARRAY), Subround.PREVOTE);

        assertEquals(0L, result);
    }

    @Test
    void testGetObservedVotesForBlockWhereVotesAreDescendantsOfProvidedBlockHash() throws Exception {
        PubKey pubKey1 = Ed25519Utils.generateKeyPair().publicKey();
        PubKey pubKey2 = Ed25519Utils.generateKeyPair().publicKey();

        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));

        Map<PubKey, Vote> prevotes = new HashMap<>();
        prevotes.put(pubKey1, firstVote);
        prevotes.put(pubKey2, secondVote);

        when(grandpaRound.getPreVotes()).thenReturn(prevotes);
        when(blockState.isDescendantOf(any(), any())).thenReturn(true);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getObservedVotesForBlock",
                Hash256.class,
                Subround.class
        );

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, new Hash256(ZEROS_ARRAY), Subround.PREVOTE);

        assertEquals(2L, result);
    }

    @Test
    void testGetTotalVotesForBlockWithoutObservedVotes() throws Exception {
        PubKey pubKey1 = Ed25519Utils.generateKeyPair().publicKey();
        PubKey pubKey2 = Ed25519Utils.generateKeyPair().publicKey();

        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));

        Map<PubKey, Vote> prevotes = new HashMap<>();
        prevotes.put(pubKey1, firstVote);
        prevotes.put(pubKey2, secondVote);

        PubKey pubKey3 = Ed25519Utils.generateKeyPair().publicKey();

        Map<PubKey, SignedVote> pvEquivocations = new HashMap<>();
        pvEquivocations.put(pubKey3, new SignedVote());

        when(grandpaRound.getPreVotes()).thenReturn(prevotes);
        when(grandpaRound.getPvEquivocations()).thenReturn(pvEquivocations);
        when(blockState.isDescendantOf(any(), any())).thenReturn(false);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getTotalVotesForBlock", Hash256.class, Subround.class);

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, new Hash256(ZEROS_ARRAY), Subround.PREVOTE);

        // Observed votes: 0
        // Equivocations: 1
        // Total votes: 0
        assertEquals(0, result);
    }

    @Test
    void testGetTotalVotesForBlockWithObservedVotes() throws Exception {
        PubKey pubKey1 = Ed25519Utils.generateKeyPair().publicKey();
        PubKey pubKey2 = Ed25519Utils.generateKeyPair().publicKey();

        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));

        Map<PubKey, Vote> prevotes = new HashMap<>();
        prevotes.put(pubKey1, firstVote);
        prevotes.put(pubKey2, secondVote);

        when(grandpaRound.getPreVotes()).thenReturn(prevotes);
        when(grandpaRound.getPvEquivocations()).thenReturn(new HashMap<>());
        when(blockState.isDescendantOf(any(), any())).thenReturn(true);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getTotalVotesForBlock", Hash256.class, Subround.class);

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, new Hash256(ZEROS_ARRAY), Subround.PREVOTE);

        // Observed votes: 2
        // Equivocations: 0
        // Total votes: 2
        assertEquals(2, result);
    }

    @Test
    void testGetTotalVotesForBlockWithObservedVotesAndEquivocations() throws Exception {
        PubKey pubKey1 = Ed25519Utils.generateKeyPair().publicKey();
        PubKey pubKey2 = Ed25519Utils.generateKeyPair().publicKey();

        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));

        Map<PubKey, Vote> prevotes = new HashMap<>();
        prevotes.put(pubKey1, firstVote);
        prevotes.put(pubKey2, secondVote);

        PubKey pubKey3 = Ed25519Utils.generateKeyPair().publicKey();

        Map<PubKey, SignedVote> pvEquivocations = new HashMap<>();
        pvEquivocations.put(pubKey3, new SignedVote());

        when(grandpaRound.getPreVotes()).thenReturn(prevotes);
        when(grandpaRound.getPvEquivocations()).thenReturn(pvEquivocations);
        when(blockState.isDescendantOf(any(), any())).thenReturn(true);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getTotalVotesForBlock", Hash256.class, Subround.class);

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, new Hash256(ZEROS_ARRAY), Subround.PREVOTE);

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

        PubKey pubKey1 = Ed25519Utils.generateKeyPair().publicKey();

        Map<PubKey, Vote> prevotes = new HashMap<>();
        prevotes.put(pubKey1, firstVote);

        when(grandpaRound.getPreVotes()).thenReturn(prevotes);
        when(grandpaRound.getPvEquivocations()).thenReturn(new HashMap<>());
        when(blockState.isDescendantOf(any(), any())).thenReturn(true);

        when(blockState.lowestCommonAncestor(new Hash256(ONES_ARRAY), new Hash256(THREES_ARRAY)))
                .thenReturn(new Hash256(ZEROS_ARRAY));

        when(blockState.getHeader(new Hash256(ZEROS_ARRAY)))
                .thenReturn(createBlockHeader());

        Method method = GrandpaService.class.getDeclaredMethod("getPossibleSelectedAncestors",
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

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                Ed25519Utils.generateKeyPair().publicKey(), firstVote,
                Ed25519Utils.generateKeyPair().publicKey(), secondVote
        ));

        when(blockState.isDescendantOf(any(), any())).thenReturn(true);
        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(3));

        Method method = GrandpaService.class.getDeclaredMethod(
                "getPossibleSelectedBlocks", BigInteger.class, Subround.class);
        method.setAccessible(true);

        Map<Hash256, BigInteger> result = (Map<Hash256, BigInteger>) method.invoke(
                grandpaService, BigInteger.valueOf(1), Subround.PREVOTE);

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
