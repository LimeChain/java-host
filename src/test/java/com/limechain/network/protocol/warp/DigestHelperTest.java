package com.limechain.network.protocol.warp;

import com.limechain.babe.state.EpochState;
import com.limechain.network.protocol.warp.dto.ConsensusEngine;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.utils.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DigestHelperTest {

    @InjectMocks
    private DigestHelper digestHelper;

    @Mock
    private EpochState epochState;

    @Test
    void handleHeaderDigests_WithConsensusAndPreRuntimeDigests_ShouldProcessBothCorrectly() {
        byte[] consensusMessage = new byte[]{1, 2, 3};
        byte[] preRuntimeMessage = StringUtils.hexToBytes("0x03b7010000286f301100000000424dc7bc71c9b0f3a15b6aba389471fff57e154dbf3dc046f47513a38375ce4cfe421df446b2b7a0f5b1b2e69c810a6ba36787c0301e6636df3e45688116e005363dfe9922b3b7cc7b80e2fdab8b0b98dcfb4b96cc470fb35f753debda40aa0e");

        HeaderDigest consensusDigest = mock(HeaderDigest.class);
        HeaderDigest preRuntimeDigest = mock(HeaderDigest.class);

        when(consensusDigest.getType()).thenReturn(DigestType.CONSENSUS_MESSAGE);
        when(consensusDigest.getId()).thenReturn(ConsensusEngine.BABE);
        when(consensusDigest.getMessage()).thenReturn(consensusMessage);

        when(preRuntimeDigest.getType()).thenReturn(DigestType.PRE_RUNTIME);
        when(preRuntimeDigest.getId()).thenReturn(ConsensusEngine.BABE);
        when(preRuntimeDigest.getMessage()).thenReturn(preRuntimeMessage);

        HeaderDigest[] headerDigests = {consensusDigest, preRuntimeDigest};

        digestHelper.handleHeaderDigests(headerDigests);

        verify(epochState).updateNextEpochBlockConfig(consensusMessage);
    }
}
