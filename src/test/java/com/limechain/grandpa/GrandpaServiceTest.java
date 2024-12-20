package com.limechain.grandpa;

import com.limechain.grandpa.state.GrandpaState;
import com.limechain.grandpa.state.Subround;
import com.limechain.network.protocol.grandpa.messages.commit.Vote;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrandpaServiceTest {

    private static final byte[] ZEROS_ARRAY = new byte[32];
    private static final byte[] ONES_ARRAY =
            new byte[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    private static final byte[] TWOS_ARRAY =
            new byte[]{2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};

    private GrandpaState grandpaState;
    private BlockState blockState;
    private GrandpaService grandpaService;

    @BeforeEach
    void setUp() {
        grandpaState = mock(GrandpaState.class);
        blockState = mock(BlockState.class);
        grandpaService = new GrandpaService(grandpaState, blockState);
    }

    @Test
    void testGetGrandpaGHOSTWhereNoBlocksPassThreshold() {
        when(grandpaState.getThreshold()).thenReturn(BigInteger.valueOf(10));
        when(grandpaState.getPrevotes()).thenReturn(Map.of());

        var result = grandpaService.getGrandpaGHOST();
        assertNull(result);
    }

    @Test
    void testGetGrandpaGHOSTWithBlockPassingThreshold() {
        when(grandpaState.getThreshold()).thenReturn(BigInteger.valueOf(1));

        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));
        Vote secondVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));

        when(grandpaState.getPrevotes()).thenReturn(Map.of(
                Ed25519Utils.generateKeyPair().publicKey(), firstVote,
                Ed25519Utils.generateKeyPair().publicKey(), secondVote
        ));

        BlockHeader blockHeader = createBlockHeader();
        blockHeader.setBlockNumber(BigInteger.valueOf(1));
        when(blockState.getHighestFinalizedHeader()).thenReturn(blockHeader);

        when(blockState.isDescendantOf(firstVote.getBlockHash(), firstVote.getBlockHash())).thenReturn(true);
        when(blockState.isDescendantOf(secondVote.getBlockHash(), secondVote.getBlockHash())).thenReturn(true);

        Vote result = grandpaService.getGrandpaGHOST();
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

        when(grandpaState.getPrevotes()).thenReturn(prevotes);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("getDirectVotes", Subround.class);
        method.setAccessible(true);

        Map<Vote, Long> result = (HashMap<Vote, Long>) method.invoke(grandpaService, Subround.PRE_VOTE);

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

        when(grandpaState.getPrevotes()).thenReturn(prevotes);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("getDirectVotes", Subround.class);
        method.setAccessible(true);

        Map<Vote, Long> result = (HashMap<Vote, Long>) method.invoke(grandpaService, Subround.PRE_VOTE);

        assertEquals(2L, result.get(firstVote));
    }

    @Test
    void testGetVotes() throws Exception {
        // Prepare mock data
        PubKey pubKey1 = Ed25519Utils.generateKeyPair().publicKey();

        Vote firstVote = new Vote(new Hash256(ONES_ARRAY), BigInteger.valueOf(3));

        Map<PubKey, Vote> prevotes = new HashMap<>();
        prevotes.put(pubKey1, firstVote);

        when(grandpaState.getPrevotes()).thenReturn(prevotes);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("getVotes", Subround.class);
        method.setAccessible(true);

        List<Vote> result = (List<Vote>) method.invoke(grandpaService, Subround.PRE_VOTE);

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

        when(grandpaState.getPrevotes()).thenReturn(prevotes);

        // Call the private method via reflection
        Method method = GrandpaService.class.getDeclaredMethod("getVotes", Subround.class);
        method.setAccessible(true);

        List<Vote> result = (List<Vote>) method.invoke(grandpaService, Subround.PRE_VOTE);

        assertTrue(result.contains(firstVote));
        assertTrue(result.contains(secondVote));
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
