package com.limechain.babe;

import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.state.EpochData;
import com.limechain.babe.state.EpochDescriptor;
import com.limechain.babe.state.EpochState;
import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.network.protocol.warp.DigestHelper;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.rpc.server.AppBean;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockProductionVerifierTest {

    @Mock
    private EpochState epochState;

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
    private final BigInteger slotNumber = BigInteger.TEN;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testVerifyAuthorship_ValidCase() {
        try (MockedStatic<AppBean> mockedAppBean = mockStatic(AppBean.class);
             MockedStatic<DigestHelper> mockedDigestHelper = mockStatic(DigestHelper.class);
             MockedStatic<Sr25519Utils> mockedSr25519 = mockStatic(Sr25519Utils.class);
             MockedStatic<VrfOutputAndProof> mockedVrfOutputAndProof = mockStatic(VrfOutputAndProof.class)) {

            BlockProductionVerifier verifierSpy = spy(blockProductionVerifier);

            mockedAppBean.when(() -> AppBean.getBean(EpochState.class)).thenReturn(epochState);

            HeaderDigest[] headerDigests = new HeaderDigest[]{sealDigest};
            when(blockHeader.getDigest()).thenReturn(headerDigests);
            when(blockHeader.getHashBytes()).thenReturn(new byte[]{0x01, 0x02, 0x03});

            when(sealDigest.getType()).thenReturn(DigestType.SEAL);
            when(sealDigest.getMessage()).thenReturn(new byte[]{0x04, 0x05});

            mockedDigestHelper.when(() -> DigestHelper.getBabePreRuntimeDigest(headerDigests))
                    .thenReturn(Optional.of(babePreDigest));

            when(babePreDigest.getAuthorityIndex()).thenReturn(0L);
            when(babePreDigest.getVrfOutput()).thenReturn(new byte[]{0x06});
            when(babePreDigest.getVrfProof()).thenReturn(new byte[]{0x07});

            VrfOutputAndProof mockVrfOutputAndProof = mock(VrfOutputAndProof.class);
            mockedVrfOutputAndProof.when(() -> VrfOutputAndProof.wrap(new byte[]{0x06}, new byte[]{0x07}))
                    .thenReturn(mockVrfOutputAndProof);

            when(epochState.getCurrentEpochData()).thenReturn(currentEpochData);
            when(epochState.getCurrentEpochIndex()).thenReturn(BigInteger.ONE);
            when(epochState.getCurrentSlotNumber()).thenReturn(BigInteger.TEN);

            when(currentEpochData.getRandomness()).thenReturn(new byte[]{0x08});
            when(currentEpochData.getAuthorities()).thenReturn(List.of(
                    new Authority(new byte[]{0x01}, BigInteger.ONE)
            ));

            mockedSr25519.when(() -> Sr25519Utils.verifySignature(any())).thenReturn(true);

            doReturn(true).when(verifierSpy).verifySlotWinner(
                    eq(0),
                    anyList(),
                    eq(BigInteger.ONE),
                    eq(new byte[]{0x08}),
                    eq(BigInteger.TEN),
                    eq(mockVrfOutputAndProof)
            );

            boolean result = verifierSpy.verifyAuthorship(blockHeader);

            assertTrue(result);

            mockedSr25519.verify(() -> Sr25519Utils.verifySignature(any()), times(1));
            mockedDigestHelper.verify(() -> DigestHelper.getBabePreRuntimeDigest(headerDigests), times(1));
            mockedVrfOutputAndProof.verify(() -> VrfOutputAndProof.wrap(new byte[]{0x06}, new byte[]{0x07}), times(1));

            verify(verifierSpy, times(1)).verifySlotWinner(
                    eq(0),
                    anyList(),
                    eq(BigInteger.ONE),
                    eq(new byte[]{0x08}),
                    eq(BigInteger.TEN),
                    eq(mockVrfOutputAndProof)
            );
        }
    }


    @Test
    void testVerifyAuthorship_InvalidSealDigest() {
        HeaderDigest[] headerDigests = new HeaderDigest[]{sealDigest};
        when(blockHeader.getDigest()).thenReturn(headerDigests);
        when(sealDigest.getType()).thenReturn(DigestType.PRE_RUNTIME);

        boolean result = blockProductionVerifier.verifyAuthorship(blockHeader);
        assertFalse(result);
    }

    @Test
    void testVerifySlotWinner_BelowThresholdAndValidVrf() {
        try (MockedStatic<Schnorrkel> mockedSchnorrkel = mockStatic(Schnorrkel.class)) {
            Schnorrkel schnorrkelMock = mock(Schnorrkel.class);
            mockedSchnorrkel.when(Schnorrkel::getInstance).thenReturn(schnorrkelMock);

            when(schnorrkelMock.makeBytes(any(Schnorrkel.PublicKey.class), any(TranscriptData.class), eq(vrfOutputAndProof)))
                    .thenReturn(new byte[32]);
            when(schnorrkelMock.vrfVerify(any(Schnorrkel.PublicKey.class), any(TranscriptData.class), eq(vrfOutputAndProof)))
                    .thenReturn(true);

            List<Authority> authorities = List.of(
                    new Authority(new byte[32], BigInteger.valueOf(1000)),
                    new Authority(new byte[32], BigInteger.valueOf(500)),
                    new Authority(new byte[32], BigInteger.valueOf(500)));
            when(epochState.getCurrentEpochData()).thenReturn(currentEpochData);
            when(epochState.getCurrentEpochDescriptor()).thenReturn(epochDescriptor);
            when(epochDescriptor.getConstant()).thenReturn(new Pair<>(BigInteger.valueOf(3), BigInteger.valueOf(4)));
            when(currentEpochData.getAuthorities()).thenReturn(authorities);
            when(vrfOutputAndProof.getOutput()).thenReturn(new byte[32]);

            boolean result = blockProductionVerifier.verifySlotWinner(0, authorities,
                    epochIndex, randomness, slotNumber, vrfOutputAndProof);

            assertTrue(result);
        }
    }

    @Test
    void testVerifySlotWinner_InvalidVrfOutput() {
        try (MockedStatic<Schnorrkel> mockedSchnorrkel = mockStatic(Schnorrkel.class)) {
            Schnorrkel schnorrkelMock = mock(Schnorrkel.class);
            mockedSchnorrkel.when(Schnorrkel::getInstance).thenReturn(schnorrkelMock);

            when(schnorrkelMock.makeBytes(any(Schnorrkel.PublicKey.class), any(TranscriptData.class), eq(vrfOutputAndProof)))
                    .thenReturn(new byte[32]);
            when(schnorrkelMock.vrfVerify(any(Schnorrkel.PublicKey.class), any(TranscriptData.class), eq(vrfOutputAndProof)))
                    .thenReturn(false);

            List<Authority> authorities = List.of(
                    new Authority(new byte[]{0x01, 0x02, 0x03}, BigInteger.valueOf(1000)),
                    new Authority(new byte[32], BigInteger.valueOf(500)),
                    new Authority(new byte[32], BigInteger.valueOf(500)));

            when(epochState.getCurrentEpochData()).thenReturn(currentEpochData);
            when(epochState.getCurrentEpochDescriptor()).thenReturn(epochDescriptor);
            when(epochDescriptor.getConstant()).thenReturn(new Pair<>(BigInteger.valueOf(3), BigInteger.valueOf(4)));
            when(currentEpochData.getAuthorities()).thenReturn(authorities);
            when(vrfOutputAndProof.getOutput()).thenReturn(new byte[32]);

            boolean result = blockProductionVerifier.verifySlotWinner(0, authorities,
                    epochIndex, randomness, slotNumber, vrfOutputAndProof);

            assertFalse(result);
        }
    }
}

