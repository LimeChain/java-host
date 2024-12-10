package com.limechain.babe;

import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.predigest.PreDigestType;
import com.limechain.babe.state.EpochData;
import com.limechain.babe.state.EpochDescriptor;
import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.exception.misc.AuthorshipVerificationException;
import com.limechain.runtime.Runtime;
import com.limechain.network.protocol.warp.DigestHelper;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.utils.Sr25519Utils;
import io.emeraldpay.polkaj.merlin.TranscriptData;
import io.emeraldpay.polkaj.schnorrkel.Schnorrkel;
import io.emeraldpay.polkaj.schnorrkel.VrfOutputAndProof;
import io.emeraldpay.polkaj.types.Hash256;
import org.javatuples.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class BlockProductionVerifierTest {

    @Mock
    private EpochData currentEpochData;

    @Mock
    private EpochDescriptor epochDescriptor;

    @InjectMocks
    private BlockProductionVerifier blockProductionVerifier;

    @Mock
    private VrfOutputAndProof vrfOutputAndProof;

    @Mock
    private BlockHeader blockHeader;

    @Mock
    private HeaderDigest sealDigest;

    @Mock
    private BabePreDigest babePreDigest;

    @Mock
    private Runtime runtime;

    private final BigInteger epochIndex = BigInteger.ONE;
    private final byte[] randomness = new byte[]{0x01, 0x02, 0x03};
    private final byte[] vrfOutput = new byte[]{0x06};
    private final byte[] vrfProof = new byte[]{0x07};

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testIsAuthorship_Valid_ValidCase() {
        try (MockedStatic<DigestHelper> mockedDigestHelper = mockStatic(DigestHelper.class);
             MockedStatic<Sr25519Utils> mockedSr25519 = mockStatic(Sr25519Utils.class);
             MockedStatic<Schnorrkel> mockedSchnorrkel = mockStatic(Schnorrkel.class);
             MockedStatic<VrfOutputAndProof> mockedVrfOutputAndProof = mockStatic(VrfOutputAndProof.class)) {
            Schnorrkel schnorrkelMock = mock(Schnorrkel.class);
            mockedSchnorrkel.when(Schnorrkel::getInstance).thenReturn(schnorrkelMock);
            BlockProductionVerifier verifierSpy = spy(blockProductionVerifier);

            when(schnorrkelMock.makeBytes(any(Schnorrkel.PublicKey.class), any(TranscriptData.class), eq(vrfOutputAndProof)))
                    .thenReturn(new byte[32]);
            when(schnorrkelMock.vrfVerify(any(Schnorrkel.PublicKey.class), any(TranscriptData.class), eq(vrfOutputAndProof)))
                    .thenReturn(true);

            HeaderDigest[] headerDigests = new HeaderDigest[]{sealDigest};
            when(blockHeader.getDigest()).thenReturn(headerDigests);
            when(blockHeader.getBlake2bHash(true)).thenReturn(new byte[]{0x01, 0x02, 0x03});

            when(sealDigest.getType()).thenReturn(DigestType.SEAL);
            when(sealDigest.getMessage()).thenReturn(new byte[]{0x04, 0x05});

            mockedDigestHelper.when(() -> DigestHelper.getBabePreRuntimeDigest(headerDigests))
                    .thenReturn(Optional.of(babePreDigest));


            when(babePreDigest.getType()).thenReturn(PreDigestType.BABE_PRIMARY);
            when(babePreDigest.getSlotNumber()).thenReturn(BigInteger.TEN);
            when(babePreDigest.getAuthorityIndex()).thenReturn(0L);
            when(babePreDigest.getVrfOutput()).thenReturn(vrfOutput);
            when(babePreDigest.getVrfProof()).thenReturn(vrfProof);

            mockedVrfOutputAndProof.when(() -> VrfOutputAndProof.wrap(vrfOutput, vrfProof))
                    .thenReturn(vrfOutputAndProof);

            when(currentEpochData.getRandomness()).thenReturn(randomness);
            when(epochDescriptor.getConstant()).thenReturn(new Pair<>(BigInteger.valueOf(3), BigInteger.valueOf(4)));
            List<Authority> authorities = List.of(
                    new Authority(new byte[32], BigInteger.valueOf(1000)),
                    new Authority(new byte[32], BigInteger.valueOf(500)),
                    new Authority(new byte[32], BigInteger.valueOf(500)));
            when(currentEpochData.getAuthorities()).thenReturn(authorities);

            mockedSr25519.when(() -> Sr25519Utils.verifySignature(any())).thenReturn(true);

            boolean result = verifierSpy.isAuthorshipValid(runtime, blockHeader, currentEpochData, epochDescriptor, epochIndex);

            assertTrue(result);

            mockedSr25519.verify(() -> Sr25519Utils.verifySignature(any()), times(1));
            mockedDigestHelper.verify(() -> DigestHelper.getBabePreRuntimeDigest(headerDigests), times(1));
            mockedVrfOutputAndProof.verify(() -> VrfOutputAndProof.wrap(vrfOutput, vrfProof), times(1));
        }
    }

    @Test
    void testIsAuthorship_EquivocationCase() {
        try (MockedStatic<DigestHelper> mockedDigestHelper = mockStatic(DigestHelper.class);
             MockedStatic<Sr25519Utils> mockedSr25519 = mockStatic(Sr25519Utils.class);
             MockedStatic<Schnorrkel> mockedSchnorrkel = mockStatic(Schnorrkel.class);
             MockedStatic<VrfOutputAndProof> mockedVrfOutputAndProof = mockStatic(VrfOutputAndProof.class)) {
            Schnorrkel schnorrkelMock = mock(Schnorrkel.class);
            mockedSchnorrkel.when(Schnorrkel::getInstance).thenReturn(schnorrkelMock);
            BlockProductionVerifier verifierSpy = spy(blockProductionVerifier);

            when(schnorrkelMock.makeBytes(any(Schnorrkel.PublicKey.class), any(TranscriptData.class), eq(vrfOutputAndProof)))
                    .thenReturn(new byte[32]);
            when(schnorrkelMock.vrfVerify(any(Schnorrkel.PublicKey.class), any(TranscriptData.class), eq(vrfOutputAndProof)))
                    .thenReturn(true);

            HeaderDigest[] headerDigests = new HeaderDigest[]{sealDigest};
            when(blockHeader.getDigest()).thenReturn(headerDigests);
            when(blockHeader.getBlake2bHash(true)).thenReturn(new byte[]{0x01, 0x02, 0x03});

            when(sealDigest.getType()).thenReturn(DigestType.SEAL);
            when(sealDigest.getMessage()).thenReturn(new byte[]{0x04, 0x05});

            mockedDigestHelper.when(() -> DigestHelper.getBabePreRuntimeDigest(headerDigests))
                    .thenReturn(Optional.of(babePreDigest));

            when(babePreDigest.getType()).thenReturn(PreDigestType.BABE_PRIMARY);
            when(babePreDigest.getSlotNumber()).thenReturn(BigInteger.TEN);
            when(babePreDigest.getAuthorityIndex()).thenReturn(0L);
            when(babePreDigest.getVrfOutput()).thenReturn(vrfOutput);
            when(babePreDigest.getVrfProof()).thenReturn(vrfProof);

            mockedVrfOutputAndProof.when(() -> VrfOutputAndProof.wrap(vrfOutput, vrfProof))
                    .thenReturn(vrfOutputAndProof);

            when(currentEpochData.getRandomness()).thenReturn(randomness);
            when(epochDescriptor.getConstant()).thenReturn(new Pair<>(BigInteger.valueOf(3), BigInteger.valueOf(4)));
            List<Authority> authorities = List.of(
                    new Authority(new byte[32], BigInteger.valueOf(1000)),
                    new Authority(new byte[32], BigInteger.valueOf(500)),
                    new Authority(new byte[32], BigInteger.valueOf(500)));
            when(currentEpochData.getAuthorities()).thenReturn(authorities);

            mockedSr25519.when(() -> Sr25519Utils.verifySignature(any())).thenReturn(true);

            byte[] hash1 = new byte[]{
                    0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                    0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
                    0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
                    0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
            };
            byte[] hash2 = new byte[]{
                    0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28,
                    0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F, 0x30,
                    0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38,
                    0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F, 0x40
            };
            // First block header
            BlockHeader firstBlockHeader = mock(BlockHeader.class);
            when(firstBlockHeader.getDigest()).thenReturn(headerDigests);
            when(firstBlockHeader.getBlockNumber()).thenReturn(BigInteger.ONE);
            when(firstBlockHeader.getBlake2bHash(true)).thenReturn(new byte[]{0x01, 0x02, 0x03});
            when(firstBlockHeader.getHash()).thenReturn(new Hash256(hash1));

            // Second block header
            BlockHeader secondBlockHeader = mock(BlockHeader.class);
            when(secondBlockHeader.getDigest()).thenReturn(headerDigests);
            when(secondBlockHeader.getBlockNumber()).thenReturn(BigInteger.ONE); // Same block number to simulate equivocation
            when(secondBlockHeader.getBlake2bHash(true)).thenReturn(new byte[]{0x01, 0x02, 0x03, 0x04});
            when(secondBlockHeader.getHash()).thenReturn(new Hash256(hash2));

            verifierSpy.isAuthorshipValid(runtime, firstBlockHeader, currentEpochData, epochDescriptor, epochIndex);
            boolean result = verifierSpy.isAuthorshipValid(runtime, secondBlockHeader, currentEpochData, epochDescriptor, epochIndex);
            assertFalse(result);
        }
    }

    @Test
    void testIsAuthorship_Valid_NotSlotWinner() {
        try (MockedStatic<DigestHelper> mockedDigestHelper = mockStatic(DigestHelper.class);
             MockedStatic<Sr25519Utils> mockedSr25519 = mockStatic(Sr25519Utils.class);
             MockedStatic<Schnorrkel> mockedSchnorrkel = mockStatic(Schnorrkel.class);
             MockedStatic<VrfOutputAndProof> mockedVrfOutputAndProof = mockStatic(VrfOutputAndProof.class)) {
            Schnorrkel schnorrkelMock = mock(Schnorrkel.class);
            mockedSchnorrkel.when(Schnorrkel::getInstance).thenReturn(schnorrkelMock);
            BlockProductionVerifier verifierSpy = spy(blockProductionVerifier);

            when(schnorrkelMock.makeBytes(any(Schnorrkel.PublicKey.class), any(TranscriptData.class), eq(vrfOutputAndProof)))
                    .thenReturn(new byte[32]);
            when(schnorrkelMock.vrfVerify(any(Schnorrkel.PublicKey.class), any(TranscriptData.class), eq(vrfOutputAndProof)))
                    .thenReturn(false);

            HeaderDigest[] headerDigests = new HeaderDigest[]{sealDigest};
            when(blockHeader.getDigest()).thenReturn(headerDigests);
            when(blockHeader.getBlake2bHash(false)).thenReturn(new byte[]{0x01, 0x02, 0x03});

            when(sealDigest.getType()).thenReturn(DigestType.SEAL);
            when(sealDigest.getMessage()).thenReturn(new byte[]{0x04, 0x05});

            mockedDigestHelper.when(() -> DigestHelper.getBabePreRuntimeDigest(headerDigests))
                    .thenReturn(Optional.of(babePreDigest));

            when(babePreDigest.getType()).thenReturn(PreDigestType.BABE_PRIMARY);
            when(babePreDigest.getSlotNumber()).thenReturn(BigInteger.TEN);
            when(babePreDigest.getAuthorityIndex()).thenReturn(0L);
            when(babePreDigest.getVrfOutput()).thenReturn(vrfOutput);
            when(babePreDigest.getVrfProof()).thenReturn(vrfProof);

            mockedVrfOutputAndProof.when(() -> VrfOutputAndProof.wrap(vrfOutput, vrfProof))
                    .thenReturn(vrfOutputAndProof);

            when(currentEpochData.getRandomness()).thenReturn(randomness);
            when(epochDescriptor.getConstant()).thenReturn(new Pair<>(BigInteger.valueOf(3), BigInteger.valueOf(4)));
            List<Authority> authorities = List.of(
                    new Authority(new byte[32], BigInteger.valueOf(1000)),
                    new Authority(new byte[32], BigInteger.valueOf(500)),
                    new Authority(new byte[32], BigInteger.valueOf(500)));
            when(currentEpochData.getAuthorities()).thenReturn(authorities);

            mockedSr25519.when(() -> Sr25519Utils.verifySignature(any())).thenReturn(true);

            boolean result = verifierSpy.isAuthorshipValid(runtime, blockHeader, currentEpochData, epochDescriptor, epochIndex);

            assertFalse(result);

            mockedDigestHelper.verify(() -> DigestHelper.getBabePreRuntimeDigest(headerDigests), times(1));
            mockedVrfOutputAndProof.verify(() -> VrfOutputAndProof.wrap(vrfOutput, vrfProof), times(1));
        }
    }


    @Test
    void testIsAuthorship_Valid_InvalidDigest() {
        HeaderDigest[] headerDigests = new HeaderDigest[]{sealDigest};
        when(blockHeader.getDigest()).thenReturn(headerDigests);
        when(sealDigest.getType()).thenReturn(DigestType.PRE_RUNTIME);

        assertThrows(AuthorshipVerificationException.class, () -> blockProductionVerifier.isAuthorshipValid(runtime, blockHeader,
                currentEpochData,
                epochDescriptor,
                BigInteger.ONE)
        );
    }
}

