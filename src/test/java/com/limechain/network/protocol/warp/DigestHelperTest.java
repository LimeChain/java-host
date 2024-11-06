package com.limechain.network.protocol.warp;

import com.limechain.babe.state.EpochState;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.ConsensusEngine;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.utils.StringUtils;
import io.emeraldpay.polkaj.schnorrkel.Schnorrkel;
import io.emeraldpay.polkaj.schnorrkel.SchnorrkelException;
import io.emeraldpay.polkaj.types.Hash256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
public class DigestHelperTest {

    @InjectMocks
    private DigestHelper digestHelper;

    @Mock
    private EpochState epochState;

    //TODO: This will be fixed in https://github.com/LimeChain/Fruzhin/issues/593
//    @Test
//    void handleHeaderDigests_WithConsensusAndPreRuntimeDigests_ShouldProcessBothCorrectly() {
//        byte[] consensusMessage = new byte[]{1, 2, 3};
//        byte[] preRuntimeMessage = StringUtils.hexToBytes("0x03b7010000286f301100000000424dc7bc71c9b0f3a15b6aba389471fff57e154dbf3dc046f47513a38375ce4cfe421df446b2b7a0f5b1b2e69c810a6ba36787c0301e6636df3e45688116e005363dfe9922b3b7cc7b80e2fdab8b0b98dcfb4b96cc470fb35f753debda40aa0e");
//
//        HeaderDigest consensusDigest = mock(HeaderDigest.class);
//        HeaderDigest preRuntimeDigest = mock(HeaderDigest.class);
//
//        when(consensusDigest.getType()).thenReturn(DigestType.CONSENSUS_MESSAGE);
//        when(consensusDigest.getId()).thenReturn(ConsensusEngine.BABE);
//        when(consensusDigest.getMessage()).thenReturn(consensusMessage);
//
//        when(preRuntimeDigest.getType()).thenReturn(DigestType.PRE_RUNTIME);
//        when(preRuntimeDigest.getId()).thenReturn(ConsensusEngine.BABE);
//        when(preRuntimeDigest.getMessage()).thenReturn(preRuntimeMessage);
//
//        HeaderDigest[] headerDigests = {consensusDigest, preRuntimeDigest};
//
//        digestHelper.handleHeaderDigests(headerDigests);
//
//        verify(epochState).updateNextEpochBlockConfig(consensusMessage);
//    }

    @Test
    void testBuildSealHeaderDigest() throws SchnorrkelException {
        BlockHeader blockHeader = new BlockHeader();
        blockHeader.setParentHash(new Hash256(StringUtils.hexToBytes("0xc222afdf6e4c005e0d416ca8359792e8eb23f51dd881ef3efba51fa4611b9a60")));
        blockHeader.setBlockNumber(BigInteger.valueOf(23280976));
        blockHeader.setStateRoot(new Hash256(StringUtils.hexToBytes("0xcb29143dc0be744f9fe43c761123e78f28cab6695470e785baf20c8d4a7e1673")));
        blockHeader.setExtrinsicsRoot(new Hash256(StringUtils.hexToBytes("0x6ba9184bfc480cce07a1100cd84ffa484c11b5909d9e6c689792909b4621aeb3")));

        HeaderDigest preRuntimeDigest = new HeaderDigest();
        preRuntimeDigest.setType(DigestType.PRE_RUNTIME);
        preRuntimeDigest.setId(ConsensusEngine.BABE);
        preRuntimeDigest.setMessage(StringUtils.hexToBytes("0x03930000004fb631110000000092663f387c34042231ee717ecc19f18841cae377fc9a1bef3b8af686dd77b1337aabf770a622251c8bb01c148cf9ad8b0f46dacfc4b01423622090d3178885098052975353f07f3a2c983d46459298559b0186f871ebdf4472916dd89da33903"));

        HeaderDigest[] headerDigests = new HeaderDigest[]{preRuntimeDigest};
        blockHeader.setDigest(headerDigests);

        Schnorrkel.KeyPair keyPair = Schnorrkel.getInstance().generateKeyPair(new SecureRandom());

        HeaderDigest sealHeaderDigest = digestHelper.buildSealHeaderDigest(blockHeader, keyPair);

        assertNotNull(sealHeaderDigest);
        assertEquals(DigestType.SEAL, sealHeaderDigest.getType());
        assertEquals(ConsensusEngine.BABE, sealHeaderDigest.getId());
    }
}
