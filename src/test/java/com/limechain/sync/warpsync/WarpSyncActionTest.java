package com.limechain.sync.warpsync;

import com.google.protobuf.ByteString;
import com.limechain.exception.global.RuntimeCodeException;
import com.limechain.network.Network;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.lightclient.pb.LightClientMessage;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.network.request.ProtocolRequester;
import com.limechain.runtime.Runtime;
import com.limechain.runtime.builder.RuntimeBuilder;
import com.limechain.storage.DBConstants;
import com.limechain.storage.KVRepository;
import com.limechain.storage.block.SyncState;
import com.limechain.trie.decoded.Trie;
import com.limechain.trie.decoded.TrieVerifier;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.types.Hash256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unused")
@ExtendWith(MockitoExtension.class)
class WarpSyncActionTest {
    @InjectMocks
    @Spy
    private WarpSyncState warpSyncState;
    @Mock
    private Set<BigInteger> scheduledRuntimeUpdateBlocks;
    @Mock
    private SyncState syncState;
    @Mock
    private RuntimeBuilder runtimeBuilder;
    @Mock
    private KVRepository<String, Object> repository;
    @Mock
    private Network network;
    @Mock
    private Hash256 lastFinalizedBlockHash;
    @Mock
    private Hash256 stateRoot;
    @Mock
    private Trie mockTrie;
    @Mock
    private ProtocolRequester requester;

    @Test
    void updateRuntimeCode() throws RuntimeCodeException {
        LightClientMessage.Response response = mock(LightClientMessage.Response.class);
        LightClientMessage.RemoteReadResponse remoteReadResponse = mock(LightClientMessage.RemoteReadResponse.class);
        ByteString wrappedProof = mock(ByteString.class);
        byte[] proof = new byte[]{1, 2, 3};
        byte[] decodedProof = new byte[]{4, 5, 6};
        byte[][] decodedProofs = new byte[][]{decodedProof};
        String blockHashString = "blhash";
        String[] codeKey = new String[]{WarpSyncState.CODE_KEY};
        String stateRootString = "state root";
        byte[] runtimeCode = new byte[]{1, 2};
        when(syncState.getLastFinalizedBlockHash()).thenReturn(lastFinalizedBlockHash);
        when(lastFinalizedBlockHash.toString()).thenReturn(blockHashString);
        when(syncState.getStateRoot()).thenReturn(stateRoot);
        when(requester.makeRemoteReadRequest(blockHashString, codeKey))
                .thenReturn(CompletableFuture.completedFuture(response));
        when(response.getRemoteReadResponse()).thenReturn(remoteReadResponse);
        when(remoteReadResponse.getProof()).thenReturn(wrappedProof);
        when(wrappedProof.toByteArray()).thenReturn(proof);

        try (MockedConstruction<ScaleCodecReader> readerMock = mockConstruction((ScaleCodecReader.class),
                (mock, context) -> {
                    when(mock.readCompactInt()).thenReturn(1);
                    when(mock.readByteArray()).thenReturn(decodedProof);
                }); MockedStatic<TrieVerifier> staticVerifier = mockStatic(TrieVerifier.class)
        ) {
            staticVerifier.when(() -> TrieVerifier.buildTrie(decodedProofs, stateRoot.getBytes())).thenReturn(mockTrie);
            when(mockTrie.get(any())).thenReturn(runtimeCode);

            warpSyncState.updateRuntimeCode();

            verify(repository).save(DBConstants.RUNTIME_CODE, runtimeCode);
            assertEquals(runtimeCode, warpSyncState.getRuntimeCode());
        }
    }

    @Test
    void syncBlockAnnounceWhenHasRunEnvUpdatedDigestShouldScheduleRuntimeUpdate() {
        BlockAnnounceMessage blockAnnounceMessage = mock(BlockAnnounceMessage.class);
        BlockHeader blockHeader = mock(BlockHeader.class);
        HeaderDigest headerDigest = mock(HeaderDigest.class);
        BigInteger blockNumber = mock(BigInteger.class);
        when(blockAnnounceMessage.getHeader()).thenReturn(blockHeader);
        when(blockHeader.getDigest()).thenReturn(new HeaderDigest[]{headerDigest});
        when(headerDigest.getType()).thenReturn(DigestType.RUN_ENV_UPDATED);
        when(blockHeader.getBlockNumber()).thenReturn(blockNumber);

        warpSyncState.syncBlockAnnounce(blockAnnounceMessage);

        verify(scheduledRuntimeUpdateBlocks).add(blockNumber);
    }

    @Test
    void syncBlockAnnounceWhenNoRunEnvUpdatedDigestShouldDoNothing() {
        BlockAnnounceMessage blockAnnounceMessage = mock(BlockAnnounceMessage.class);
        BlockHeader blockHeader = mock(BlockHeader.class);
        HeaderDigest headerDigest = mock(HeaderDigest.class);
        when(blockAnnounceMessage.getHeader()).thenReturn(blockHeader);
        when(blockHeader.getDigest()).thenReturn(new HeaderDigest[]{headerDigest});
        when(headerDigest.getType()).thenReturn(DigestType.OTHER);

        warpSyncState.syncBlockAnnounce(blockAnnounceMessage);

        verifyNoInteractions(scheduledRuntimeUpdateBlocks);
    }

    @Test
    void loadSavedRuntimeWhenValuesAvailableShouldBuildRuntimeCode() throws RuntimeCodeException {
        byte[][] merkleProof = new byte[][]{{1, 2}, {3, 4}};
        Object stateRootObject = mock(Object.class);
        String stateRootStr = "root";
        Hash256 stateRootHash = mock(Hash256.class);
        byte[] runtimeCode = new byte[]{1, 2};
        when(repository.find(DBConstants.RUNTIME_CODE)).thenReturn(Optional.of(runtimeCode));

        try (MockedStatic<Hash256> hashMock = mockStatic(Hash256.class);
             MockedStatic<TrieVerifier> staticVerifier = mockStatic(TrieVerifier.class)) {
            hashMock.when(() -> Hash256.from(stateRootStr)).thenReturn(stateRootHash);

            warpSyncState.loadSavedRuntimeCode();

            assertEquals(runtimeCode, warpSyncState.getRuntimeCode());
        }
    }

    @Test
    void loadSavedRuntimeWhenMissingRuntimeCodeShouldThrow() {
        when(repository.find(DBConstants.RUNTIME_CODE)).thenReturn(Optional.empty());

        assertThrows(RuntimeCodeException.class,
                () -> warpSyncState.loadSavedRuntimeCode(),
                "No available runtime code");
    }

    @Test
    void buildRuntime() {
        byte[] runtimeCode = new byte[]{1, 2, 3};
        warpSyncState.setRuntimeCode(runtimeCode);
        Runtime runtime = mock(Runtime.class);
        when(runtimeBuilder.buildRuntime(runtimeCode)).thenReturn(runtime);

        warpSyncState.buildRuntime();

        assertEquals(runtime, warpSyncState.getRuntime());
    }
}