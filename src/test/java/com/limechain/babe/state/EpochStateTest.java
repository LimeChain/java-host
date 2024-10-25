package com.limechain.babe.state;


import com.limechain.babe.api.BabeApiConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EpochStateTest {

    @InjectMocks
    private EpochState epochState;

    @Mock
    private BabeApiConfiguration babeApiConfiguration;

    @Test
    public void testGetCurrentSlotNumber() {
        BigInteger slotDuration = BigInteger.valueOf(6000);
        when(babeApiConfiguration.getSlotDuration()).thenReturn(slotDuration);
        epochState.initialize(babeApiConfiguration);

        Instant now = Instant.now();
        long expectedSlotNumber = now.toEpochMilli() / slotDuration.longValue();
        BigInteger currentSlotNumber = epochState.getCurrentSlotNumber();
        assertEquals(BigInteger.valueOf(expectedSlotNumber), currentSlotNumber);
    }
}
