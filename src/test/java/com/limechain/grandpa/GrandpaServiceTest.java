package com.limechain.grandpa;

import com.limechain.exception.grandpa.GrandpaGenericException;
import com.limechain.grandpa.round.GrandpaRound;
import com.limechain.grandpa.state.GrandpaSetState;
import com.limechain.grandpa.vote.SignedVote;
import com.limechain.grandpa.vote.SubRound;
import com.limechain.grandpa.vote.Vote;
import com.limechain.network.PeerMessageCoordinator;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import com.limechain.network.protocol.grandpa.messages.vote.SignedMessage;
import com.limechain.network.protocol.grandpa.messages.vote.VoteMessage;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.ConsensusEngine;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.state.AbstractState;
import com.limechain.state.StateManager;
import com.limechain.storage.block.state.BlockState;
import io.emeraldpay.polkaj.types.Hash256;
import io.emeraldpay.polkaj.types.Hash512;
import org.javatuples.Pair;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.limechain.utils.TestUtils.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// TODO Enable once proper test cases are present. Also future refactor will probably follow.
@Disabled
class GrandpaServiceTest {

    private static final byte[] ZEROS_ARRAY = new byte[32];
    private static final byte[] ONES_ARRAY =
            new byte[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    private static final byte[] TWOS_ARRAY =
            new byte[]{2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};
    private static final byte[] THREES_ARRAY =
            new byte[]{3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3};

    @Mock
    private GrandpaSetState grandpaSetState;

    @Mock
    private BlockState blockState;

    @Mock
    private GrandpaRound grandpaRound;

    @Mock
    private PeerMessageCoordinator peerMessageCoordinator;

    @Mock
    private StateManager stateManager;

    @InjectMocks
    private GrandpaService grandpaService;

    @Test
    void testFindBestFinalCandidateWithoutPreCommits() throws Exception {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));

        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);

        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(stateManager.getGrandpaSetState()).thenReturn(grandpaSetState);
        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of());
        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                firstVoteAuthorityHash, firstSignedVote,
                secondVoteAuthorityHash, secondSignedVote
        ));

        when(stateManager.getBlockState()).thenReturn(blockState);
        when(blockState.getLastFinalizedBlockAsVote())
                .thenReturn(new Vote(blockHeader.getHash(), blockHeader.getBlockNumber()));
        when(blockState.isDescendantOf(firstVote.getBlockHash(), firstVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(secondVote.getBlockHash(), secondVote.getBlockHash())).thenReturn(true);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("findBestFinalCandidate", GrandpaRound.class);
        method.setAccessible(true);

        Vote result = (Vote) method.invoke(grandpaService, grandpaRound);

        assertNotNull(result);
        assertEquals(firstVote.getBlockHash(), result.getBlockHash());
    }

    @Test
    void testFindBestFinalCandidateWithPreCommitBlockNumberBiggerThatPreVoteBlockNumber() throws Exception {
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

        when(stateManager.getGrandpaSetState()).thenReturn(grandpaSetState);
        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                firstVoteAuthorityHash, firstSignedVote,
                secondVoteAuthorityHash, secondSignedVote
        ));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of(
                thirdVoteAuthorityHash, thirdSignedVote
        ));

        when(stateManager.getBlockState()).thenReturn(blockState);
        when(blockState.getLastFinalizedBlockAsVote())
                .thenReturn(new Vote(blockHeader.getHash(), blockHeader.getBlockNumber()));
        when(blockState.isDescendantOf(firstVote.getBlockHash(), firstVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(secondVote.getBlockHash(), secondVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(thirdVote.getBlockHash(), thirdVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(thirdVote.getBlockHash(), firstVote.getBlockHash())).thenReturn(true);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("findBestFinalCandidate", GrandpaRound.class);
        method.setAccessible(true);

        Vote result = (Vote) method.invoke(grandpaService, grandpaRound);


        assertNotNull(result);
        assertEquals(thirdVote.getBlockHash(), result.getBlockHash());
    }

    @Test
    void testFindBestFinalCandidateWithPreCommitBlockNumberLessThatPreVoteBlockNumber() throws Exception {
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

        when(stateManager.getGrandpaSetState()).thenReturn(grandpaSetState);
        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                firstVoteAuthorityHash, firstSignedVote,
                secondVoteAuthorityHash, secondSignedVote
        ));

        when(grandpaRound.getPreCommits()).thenReturn(Map.of(
                thirdVoteAuthorityHash, thirdSignedVote
        ));

        when(stateManager.getBlockState()).thenReturn(blockState);
        when(blockState.getLastFinalizedBlockAsVote())
                .thenReturn(new Vote(blockHeader.getHash(), blockHeader.getBlockNumber()));
        when(blockState.isDescendantOf(firstVote.getBlockHash(), firstVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(secondVote.getBlockHash(), secondVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(thirdVote.getBlockHash(), thirdVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(thirdVote.getBlockHash(), blockHeader.getHash())).thenReturn(true);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("findBestFinalCandidate", GrandpaRound.class);
        method.setAccessible(true);

        Vote result = (Vote) method.invoke(grandpaService, grandpaRound);


        assertNotNull(result);
        assertEquals(blockHeader.getHash(), result.getBlockHash());
    }

    @Test
    void testFindBestFinalCandidateWhereRoundNumberIsZero() throws Exception {
        BlockHeader blockHeader = createBlockHeader();

        when(stateManager.getGrandpaSetState()).thenReturn(grandpaSetState);
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(0));
        when(stateManager.getBlockState()).thenReturn(blockState);
        when(blockState.getLastFinalizedBlockAsVote())
                .thenReturn(new Vote(blockHeader.getHash(), blockHeader.getBlockNumber()));

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("findBestFinalCandidate", GrandpaRound.class);
        method.setAccessible(true);

        Vote result = (Vote) method.invoke(grandpaService, grandpaRound);

        assertEquals(blockHeader.getHash(), result.getBlockHash());
        assertEquals(blockHeader.getBlockNumber(), result.getBlockNumber());
    }

    @Test
    void testFindBestPreVoteCandidate_WithSignedMessage() throws Exception {
        Vote currentVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Hash256 currentVoteAuthorityHash = new Hash256(ONES_ARRAY);
        SignedVote currentSignedVote = new SignedVote(currentVote, Hash512.empty(), currentVoteAuthorityHash);
        Vote primaryVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));
        Hash256 primaryVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote primarySignedVote = new SignedVote(primaryVote, Hash512.empty(), primaryVoteAuthorityHash);

        when(grandpaRound.getPrimaryVote()).thenReturn(primarySignedVote);
        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                currentVoteAuthorityHash, currentSignedVote
        ));

        BlockHeader bfc = new BlockHeader();
        bfc.setBlockNumber(BigInteger.TWO);

        GrandpaRound previousRound = new GrandpaRound();
        previousRound.setLastFinalizedBlock(bfc);
        when(grandpaRound.getPrevious()).thenReturn(previousRound);

        when(blockState.getLastFinalizedBlockAsVote())
                .thenReturn(new Vote(blockHeader.getHash(), blockHeader.getBlockNumber()));
        when(blockState.isDescendantOf(currentVote.getBlockHash(), currentVote.getBlockHash())).thenReturn(true);

        when(stateManager.getGrandpaSetState()).thenReturn(grandpaSetState);
        when(stateManager.getBlockState()).thenReturn(blockState);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("findBestPreVoteCandidate", GrandpaRound.class);
        method.setAccessible(true);

        Vote result = (Vote) method.invoke(grandpaService, grandpaRound);

        assertNotNull(result);
        assertEquals(new Hash256(TWOS_ARRAY), result.getBlockHash());
        assertEquals(BigInteger.valueOf(4), result.getBlockNumber());
    }

    @Test
    void testFindBestPreVoteCandidate_WithoutSignedMessage() throws Exception {
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

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                currentVoteAuthorityHash, currentSignedVote
        ));

        BlockHeader bfc = new BlockHeader();
        bfc.setBlockNumber(BigInteger.TWO);

        GrandpaRound previousRound = new GrandpaRound();
        previousRound.setLastFinalizedBlock(bfc);
        when(grandpaRound.getPrevious()).thenReturn(previousRound);

        when(blockState.getLastFinalizedBlockAsVote())
                .thenReturn(new Vote(blockHeader.getHash(), blockHeader.getBlockNumber()));
        when(blockState.isDescendantOf(currentVote.getBlockHash(), currentVote.getBlockHash())).thenReturn(true);

        when(stateManager.getGrandpaSetState()).thenReturn(grandpaSetState);
        when(stateManager.getBlockState()).thenReturn(blockState);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("findBestPreVoteCandidate", GrandpaRound.class);
        method.setAccessible(true);

        Vote result = (Vote) method.invoke(grandpaService, grandpaRound);

        assertNotNull(result);
        assertEquals(currentVote.getBlockHash(), result.getBlockHash());
        assertEquals(currentVote.getBlockNumber(), result.getBlockNumber());
    }

    @Test
    void testFindBestPreVoteCandidate_WithSignedMessageAndBlockNumberLessThanCurrentVote() throws Exception {
        Vote currentVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(4));
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

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                currentVoteAuthorityHash, currentSignedVote
        ));

        BlockHeader bfc = new BlockHeader();
        bfc.setBlockNumber(BigInteger.ONE);
        GrandpaRound previousRound = new GrandpaRound();
        previousRound.setLastFinalizedBlock(bfc);
        when(grandpaRound.getPrevious()).thenReturn(previousRound);

        when(blockState.getLastFinalizedBlockAsVote())
                .thenReturn(new Vote(blockHeader.getHash(), blockHeader.getBlockNumber()));
        when(blockState.isDescendantOf(currentVote.getBlockHash(), currentVote.getBlockHash())).thenReturn(true);

        when(stateManager.getGrandpaSetState()).thenReturn(grandpaSetState);
        when(stateManager.getBlockState()).thenReturn(blockState);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("findBestPreVoteCandidate", GrandpaRound.class);
        method.setAccessible(true);

        Vote result = (Vote) method.invoke(grandpaService, grandpaRound);

        assertNotNull(result);
        assertEquals(currentVote.getBlockHash(), result.getBlockHash());
        assertEquals(currentVote.getBlockNumber(), result.getBlockNumber());
    }

    @Test
    void testFindGrandpaGHOSTWhereNoBlocksPassThreshold() throws Exception {
        when(stateManager.getGrandpaSetState()).thenReturn(grandpaSetState);
        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(10));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getPreVotes()).thenReturn(Map.of());

        Method method = GrandpaService.class.getDeclaredMethod("findGrandpaGhost", GrandpaRound.class);
        method.setAccessible(true);

        try {
            method.invoke(grandpaService, grandpaRound);
        } catch (InvocationTargetException e) {
            assertInstanceOf(GrandpaGenericException.class, e.getCause());
        }
    }

    @Test
    void testFindGrandpaGHOSTWhereRoundNumberIsZero() throws Exception {
        BlockHeader blockHeader = createBlockHeader();

        when(stateManager.getGrandpaSetState()).thenReturn(grandpaSetState);
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(0));
        when(stateManager.getBlockState()).thenReturn(blockState);
        when(blockState.getLastFinalizedBlockAsVote())
                .thenReturn(new Vote(blockHeader.getHash(), blockHeader.getBlockNumber()));

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("findGrandpaGhost", GrandpaRound.class);
        method.setAccessible(true);

        Vote result = (Vote) method.invoke(grandpaService, grandpaRound);

        assertEquals(blockHeader.getHash(), result.getBlockHash());
        assertEquals(blockHeader.getBlockNumber(), result.getBlockNumber());
    }

    @Test
    void testFindGrandpaGHOSTWithBlockPassingThreshold() throws Exception {
        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));

        Hash256 firstVoteAuthorityHash = new Hash256(ONES_ARRAY);
        Hash256 secondVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote firstSignedVote = new SignedVote(firstVote, Hash512.empty(), firstVoteAuthorityHash);
        SignedVote secondSignedVote = new SignedVote(secondVote, Hash512.empty(), secondVoteAuthorityHash);

        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(stateManager.getGrandpaSetState()).thenReturn(grandpaSetState);
        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                firstVoteAuthorityHash, firstSignedVote,
                secondVoteAuthorityHash, secondSignedVote
        ));

        when(stateManager.getBlockState()).thenReturn(blockState);
        when(blockState.getLastFinalizedBlockAsVote())
                .thenReturn(new Vote(blockHeader.getHash(), blockHeader.getBlockNumber()));
        when(blockState.isDescendantOf(firstVote.getBlockHash(), firstVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(secondVote.getBlockHash(), secondVote.getBlockHash())).thenReturn(true);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("findGrandpaGhost", GrandpaRound.class);
        method.setAccessible(true);

        Vote result = (Vote) method.invoke(grandpaService, grandpaRound);

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
        Method method = GrandpaService.class.getDeclaredMethod("getDirectVotes", GrandpaRound.class, SubRound.class);
        method.setAccessible(true);

        Map<Vote, Long> result = (HashMap<Vote, Long>) method.invoke(grandpaService, grandpaRound, SubRound.PRE_VOTE);

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
        Method method = GrandpaService.class.getDeclaredMethod("getDirectVotes", GrandpaRound.class, SubRound.class);
        method.setAccessible(true);

        Map<Vote, Long> result = (HashMap<Vote, Long>) method.invoke(grandpaService, grandpaRound, SubRound.PRE_VOTE);

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
        Method method = GrandpaService.class.getDeclaredMethod("getVotes", GrandpaRound.class, SubRound.class);
        method.setAccessible(true);

        List<Vote> result = (List<Vote>) method.invoke(grandpaService, grandpaRound, SubRound.PRE_VOTE);

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
        Method method = GrandpaService.class.getDeclaredMethod("getVotes", GrandpaRound.class, SubRound.class);
        method.setAccessible(true);

        List<Vote> result = (List<Vote>) method.invoke(grandpaService, grandpaRound, SubRound.PRE_VOTE);

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
        when(stateManager.getBlockState()).thenReturn(blockState);
        when(blockState.isDescendantOf(any(), any())).thenReturn(false);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getObservedVotesForBlock",
                GrandpaRound.class,
                Hash256.class,
                SubRound.class
        );

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, grandpaRound, new Hash256(ZEROS_ARRAY), SubRound.PRE_VOTE);

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
        when(stateManager.getBlockState()).thenReturn(blockState);
        when(blockState.isDescendantOf(any(), any())).thenReturn(true);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getObservedVotesForBlock",
                GrandpaRound.class,
                Hash256.class,
                SubRound.class
        );

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, grandpaRound, new Hash256(ZEROS_ARRAY), SubRound.PRE_VOTE);

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
        when(blockState.isDescendantOf(any(), any())).thenReturn(false);

        when(stateManager.getBlockState()).thenReturn(blockState);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getTotalVotesForBlock", GrandpaRound.class, Hash256.class, SubRound.class);

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, grandpaRound, new Hash256(ZEROS_ARRAY), SubRound.PRE_VOTE);

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
        when(blockState.isDescendantOf(any(), any())).thenReturn(true);

        when(stateManager.getBlockState()).thenReturn(blockState);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getTotalVotesForBlock", GrandpaRound.class, Hash256.class, SubRound.class);

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, grandpaRound, new Hash256(ZEROS_ARRAY), SubRound.PRE_VOTE);

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

        when(stateManager.getBlockState()).thenReturn(blockState);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getTotalVotesForBlock", GrandpaRound.class, Hash256.class, SubRound.class);

        method.setAccessible(true);

        long result = (long) method.invoke(grandpaService, grandpaRound, new Hash256(ZEROS_ARRAY), SubRound.PRE_VOTE);

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
        when(stateManager.getBlockState()).thenReturn(blockState);
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
                SubRound.class,
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
                SubRound.PRE_VOTE,
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

        when(stateManager.getBlockState()).thenReturn(blockState);
        when(blockState.isDescendantOf(any(), any())).thenReturn(true);

        Method method = GrandpaService.class.getDeclaredMethod(
                "getPossibleSelectedBlocks", GrandpaRound.class, BigInteger.class, SubRound.class);
        method.setAccessible(true);

        Map<Hash256, BigInteger> result = (Map<Hash256, BigInteger>) method.invoke(
                grandpaService, grandpaRound, BigInteger.valueOf(1), SubRound.PRE_VOTE);

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
        when(stateManager.getBlockState()).thenReturn(blockState);
        when(blockState.getLastFinalizedBlockAsVote())
                .thenReturn(new Vote(blockHeader.getHash(), blockHeader.getBlockNumber()));

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
        when(stateManager.getBlockState()).thenReturn(blockState);
        when(blockState.getLastFinalizedBlockAsVote())
                .thenReturn(new Vote(blockHeader.getHash(), blockHeader.getBlockNumber()));

        Method method = GrandpaService.class.getDeclaredMethod("selectBlockWithMostVotes", Map.class);
        method.setAccessible(true);

        Vote result = (Vote) method.invoke(grandpaService, blocks);

        assertNotNull(result);
        assertEquals(blockHeader.getHash(), result.getBlockHash());
        assertEquals(blockHeader.getBlockNumber(), result.getBlockNumber());
    }

    @Test
    void testBroadcastCommitMessageWhenPrimaryValidator() throws Exception {
        Hash256 authorityPublicKey = new Hash256(THREES_ARRAY);
        Map<Hash256, SignedVote> signedVotes = new HashMap<>();
        Vote vote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(123L));
        SignedVote signedVote = new SignedVote(vote, Hash512.empty(), authorityPublicKey);
        signedVotes.put(authorityPublicKey, signedVote);

        GrandpaRound previousRound = new GrandpaRound();
        previousRound.setRoundNumber(BigInteger.ZERO);
        previousRound.setPreCommits(signedVotes);
        BlockHeader blockHeader = createBlockHeader();

        when(stateManager.getGrandpaSetState()).thenReturn(grandpaSetState);
        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.ONE);
        when(blockState.getLastFinalizedBlockAsVote())
                .thenReturn(new Vote(blockHeader.getHash(), blockHeader.getBlockNumber()));
        when(grandpaSetState.getSetId()).thenReturn(BigInteger.valueOf(42L));

        when(stateManager.getBlockState()).thenReturn(blockState);

        Method method = GrandpaService.class.getDeclaredMethod("broadcastCommitMessage", GrandpaRound.class);
        method.setAccessible(true);

        method.invoke(grandpaService, previousRound);

        ArgumentCaptor<CommitMessage> commitMessageCaptor = ArgumentCaptor.forClass(CommitMessage.class);
        verify(peerMessageCoordinator).sendCommitMessageToPeers(commitMessageCaptor.capture());
        CommitMessage commitMessage = commitMessageCaptor.getValue();

        assertEquals(BigInteger.valueOf(42L), commitMessage.getSetId());
        assertEquals(BigInteger.valueOf(0L), commitMessage.getRoundNumber());
        assertEquals(blockHeader.getBlockNumber(), commitMessage.getVote().getBlockNumber());
        assertEquals(blockHeader.getHash(), commitMessage.getVote().getBlockHash());
        assertEquals(1, commitMessage.getPreCommits().length);

        SignedVote precommit = commitMessage.getPreCommits()[0];
        assertEquals(vote.getBlockHash(), precommit.getVote().getBlockHash());
        assertEquals(BigInteger.valueOf(123L), precommit.getVote().getBlockNumber());
        assertEquals(Hash512.empty(), precommit.getSignature());
        assertEquals(signedVotes.get(authorityPublicKey).getAuthorityPublicKey(),
                precommit.getAuthorityPublicKey()
        );

        verify(peerMessageCoordinator, times(1))
                .sendCommitMessageToPeers(any(CommitMessage.class));
    }

    @Test
    void testBroadcastPreVoteMessage() {
        BigInteger setId = BigInteger.TEN;
        BigInteger roundNumber = BigInteger.ONE;
        when(grandpaRound.getRoundNumber()).thenReturn(roundNumber);
        when(stateManager.getGrandpaSetState()).thenReturn(grandpaSetState);
        when(stateManager.getBlockState()).thenReturn(blockState);
        when(grandpaSetState.getSetId()).thenReturn(setId);

        Vote currentVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Hash256 currentVoteAuthorityHash = new Hash256(ONES_ARRAY);
        SignedVote currentSignedVote = new SignedVote(currentVote, Hash512.empty(), currentVoteAuthorityHash);
        Vote primaryVote = new Vote(new Hash256(TWOS_ARRAY), BigInteger.valueOf(4));
        Hash256 primaryVoteAuthorityHash = new Hash256(TWOS_ARRAY);
        SignedVote primarySignedVote = new SignedVote(primaryVote, Hash512.empty(), primaryVoteAuthorityHash);

        when(grandpaRound.getPrimaryVote()).thenReturn(primarySignedVote);
        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));

        when(grandpaSetState.getThreshold()).thenReturn(BigInteger.valueOf(1));
        when(grandpaRound.getRoundNumber()).thenReturn(BigInteger.valueOf(1));

        when(grandpaRound.getPreVotes()).thenReturn(Map.of(
                currentVoteAuthorityHash, currentSignedVote
        ));

        BlockHeader bfc = new BlockHeader();
        bfc.setBlockNumber(BigInteger.TWO);

        GrandpaRound previousRound = new GrandpaRound();
        previousRound.setLastFinalizedBlock(bfc);
        when(grandpaRound.getPrevious()).thenReturn(previousRound);

        byte[] privateKey = new byte[32];
        byte[] publicKey = new byte[32];
        Pair<byte[], byte[]> grandpaKeyPair = Pair.with(publicKey, privateKey);
        try (MockedStatic<AbstractState> abstractStateMock = mockStatic(AbstractState.class)) {
            abstractStateMock.when(AbstractState::getGrandpaKeyPair).thenReturn(grandpaKeyPair);

            when(blockState.getLastFinalizedBlockAsVote())
                    .thenReturn(new Vote(blockHeader.getHash(), blockHeader.getBlockNumber()));
            when(blockState.isDescendantOf(currentVote.getBlockHash(), currentVote.getBlockHash())).thenReturn(true);

            grandpaService.broadcastPreVoteMessage(grandpaRound);

            ArgumentCaptor<VoteMessage> captor = ArgumentCaptor.forClass(VoteMessage.class);
            verify(peerMessageCoordinator).sendVoteMessageToPeers(captor.capture());

            VoteMessage capturedMessage = captor.getValue();

            assertNotNull(capturedMessage);
            assertEquals(roundNumber, capturedMessage.getRound());
            assertEquals(setId, capturedMessage.getSetId());

            SignedMessage signedMessage = capturedMessage.getMessage();
            assertNotNull(signedMessage);
            assertEquals(primaryVote.getBlockNumber(), signedMessage.getBlockNumber());
            assertEquals(primaryVote.getBlockHash(), signedMessage.getBlockHash());
            assertNotNull(signedMessage.getAuthorityPublicKey());
            assertNotNull(signedMessage.getSignature());
            assertEquals(SubRound.PRE_VOTE, signedMessage.getStage());
        }
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
