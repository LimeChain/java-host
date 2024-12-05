package com.limechain.babe;

import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.predigest.PreDigestType;
import com.limechain.babe.state.EpochData;
import com.limechain.babe.state.EpochDescriptor;
import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.exception.misc.AuthorshipVerificationException;
import com.limechain.network.protocol.warp.DigestHelper;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.utils.Sr25519Utils;
import io.emeraldpay.polkaj.merlin.TranscriptData;
import io.emeraldpay.polkaj.schnorrkel.Schnorrkel;
import io.emeraldpay.polkaj.schnorrkel.VrfOutputAndProof;
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

            boolean result = verifierSpy.isAuthorshipValid(blockHeader, currentEpochData, epochDescriptor, epochIndex);

            assertTrue(result);

            mockedSr25519.verify(() -> Sr25519Utils.verifySignature(any()), times(1));
            mockedDigestHelper.verify(() -> DigestHelper.getBabePreRuntimeDigest(headerDigests), times(1));
            mockedVrfOutputAndProof.verify(() -> VrfOutputAndProof.wrap(vrfOutput, vrfProof), times(1));
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

            boolean result = verifierSpy.isAuthorshipValid(blockHeader, currentEpochData, epochDescriptor, epochIndex);

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

        assertThrows(AuthorshipVerificationException.class, () -> blockProductionVerifier.isAuthorshipValid(blockHeader,
                currentEpochData,
                epochDescriptor,
                BigInteger.ONE)
        );
    }
}

