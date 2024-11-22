package com.limechain.babe;

import com.limechain.babe.state.EpochData;
import com.limechain.babe.state.EpochDescriptor;
import com.limechain.babe.state.EpochState;
import com.limechain.chain.lightsyncstate.Authority;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private final BigInteger epochIndex = BigInteger.ONE;
    private final byte[] randomness = new byte[]{0x01, 0x02, 0x03};
    private final BigInteger slotNumber = BigInteger.TEN;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }


    @Test
    void testVerifySlotWinner_AboveThresholdAndValidVrf() {
        try (MockedStatic<Schnorrkel> mockedSchnorrkel = mockStatic(Schnorrkel.class)) {
            Schnorrkel schnorrkelMock = mock(Schnorrkel.class);
            mockedSchnorrkel.when(Schnorrkel::getInstance).thenReturn(schnorrkelMock);

            when(schnorrkelMock.vrfVerify(any(Schnorrkel.PublicKey.class), any(TranscriptData.class), eq(vrfOutputAndProof)))
                    .thenReturn(true);

            when(epochState.getCurrentEpochData()).thenReturn(currentEpochData);
            when(epochState.getCurrentEpochDescriptor()).thenReturn(epochDescriptor);
            when(epochDescriptor.getConstant()).thenReturn(new Pair<>(BigInteger.ZERO, BigInteger.valueOf(4)));
            when(currentEpochData.getAuthorities()).thenReturn(List.of(
                    new Authority(new byte[]{0x01, 0x02, 0x03}, BigInteger.ONE),
                    new Authority(new byte[32], BigInteger.ONE),
                    new Authority(new byte[32], BigInteger.ONE)
            ));
            when(vrfOutputAndProof.getOutput()).thenReturn(new byte[]{0x01, 0x02});

            boolean result = blockProductionVerifier.verifySlotWinner(0, epochIndex, randomness, slotNumber, vrfOutputAndProof);
            assertFalse(result);
            mockedSchnorrkel.verify(Schnorrkel::getInstance, times(1));
        }
    }

    @Test
    void testVerifySlotWinner_BelowThresholdAndValidVrf() {
        try (MockedStatic<Schnorrkel> mockedSchnorrkel = mockStatic(Schnorrkel.class)) {
            Schnorrkel schnorrkelMock = mock(Schnorrkel.class);
            mockedSchnorrkel.when(Schnorrkel::getInstance).thenReturn(schnorrkelMock);

            when(schnorrkelMock.vrfVerify(any(Schnorrkel.PublicKey.class), any(TranscriptData.class), eq(vrfOutputAndProof)))
                    .thenReturn(true);

            when(epochState.getCurrentEpochData()).thenReturn(currentEpochData);
            when(epochState.getCurrentEpochDescriptor()).thenReturn(epochDescriptor);
            when(epochDescriptor.getConstant()).thenReturn(new Pair<>(BigInteger.valueOf(3), BigInteger.valueOf(4)));
            when(currentEpochData.getAuthorities()).thenReturn(List.of(
                    new Authority(new byte[]{0x01, 0x02, 0x03}, BigInteger.valueOf(1000)),
                    new Authority(new byte[32], BigInteger.valueOf(500)),
                    new Authority(new byte[32], BigInteger.valueOf(500))
            ));
            when(vrfOutputAndProof.getOutput()).thenReturn(new byte[]{0x01, 0x02});

            boolean result = blockProductionVerifier.verifySlotWinner(0, epochIndex, randomness, slotNumber, vrfOutputAndProof);
            assertTrue(result);
            mockedSchnorrkel.verify(Schnorrkel::getInstance, times(1));
        }
    }


    @Test
    void testVerifySlotWinner_InvalidVrfOutput() {
        try (MockedStatic<Schnorrkel> mockedSchnorrkel = mockStatic(Schnorrkel.class)) {
            Schnorrkel schnorrkelMock = mock(Schnorrkel.class);
            mockedSchnorrkel.when(Schnorrkel::getInstance).thenReturn(schnorrkelMock);

            when(schnorrkelMock.vrfVerify(any(Schnorrkel.PublicKey.class), any(TranscriptData.class), eq(vrfOutputAndProof)))
                    .thenReturn(false);

            when(epochState.getCurrentEpochData()).thenReturn(currentEpochData);
            when(epochState.getCurrentEpochDescriptor()).thenReturn(epochDescriptor);
            when(epochDescriptor.getConstant()).thenReturn(new Pair<>(BigInteger.valueOf(3), BigInteger.valueOf(4)));
            when(currentEpochData.getAuthorities()).thenReturn(List.of(
                    new Authority(new byte[]{0x01, 0x02, 0x03}, BigInteger.valueOf(1000)),
                    new Authority(new byte[32], BigInteger.valueOf(500)),
                    new Authority(new byte[32], BigInteger.valueOf(500))
            ));
            when(vrfOutputAndProof.getOutput()).thenReturn(new byte[]{0x01, 0x02});

            boolean result = blockProductionVerifier.verifySlotWinner(0, epochIndex, randomness, slotNumber, vrfOutputAndProof);
            assertFalse(result);
            mockedSchnorrkel.verify(Schnorrkel::getInstance, times(1));
        }
    }
}
