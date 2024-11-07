package com.limechain.network.protocol.warp;

import com.limechain.babe.consensus.BabeConsensusMessageFormat;
import com.limechain.babe.predigest.PreDigestType;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.ConsensusEngine;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.utils.StringUtils;
import io.emeraldpay.polkaj.schnorrkel.Schnorrkel;
import io.emeraldpay.polkaj.schnorrkel.SchnorrkelException;
import io.emeraldpay.polkaj.types.Hash256;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigestHelperTest {

    @Test
    void getBabeConsensusMessageTest() {
        HeaderDigest consensusDigest = new HeaderDigest();
        consensusDigest.setId(ConsensusEngine.BABE);
        consensusDigest.setType(DigestType.CONSENSUS_MESSAGE);

        // Create a consensus message with type DISABLED_AUTHORITY(2) and value equal to 0
        var message = new byte[33];
        message[0] = 2;
        consensusDigest.setMessage(message);

        HeaderDigest[] headerDigests = new HeaderDigest[] {consensusDigest};
        var optResult = DigestHelper.getBabeConsensusMessage(headerDigests);

        assertTrue(optResult.isPresent());

        var result = optResult.get();
        assertEquals(BabeConsensusMessageFormat.DISABLED_AUTHORITY, result.getFormat());
        assertEquals(0, result.getDisabledAuthority());
        assertNull(result.getNextEpochData());
        assertNull(result.getNextEpochDescriptor());
    }

    @Test
    void getBabeConsensusMessageWithoutSuchDigestInHeadersTest() {
        var optResult = DigestHelper.getBabeConsensusMessage(new HeaderDigest[0]);
        assertTrue(optResult.isEmpty());
    }

    @Test
    void getBabePreRuntimeDigestForPrimarySlotTest() {
        HeaderDigest consensusDigest = new HeaderDigest();
        consensusDigest.setId(ConsensusEngine.BABE);
        consensusDigest.setType(DigestType.PRE_RUNTIME);

        // Create a PreRuntimeDigest with:
        // Type -> BABE_PRIMARY(1)
        // Authority index -> 0
        // Slot number -> 0
        // VRF_OUTPUT -> byte array with 32 zeros
        // VRF_PROOF -> byte array with 64 zeros
        var message = new byte[193];
        message[0] = 1;
        consensusDigest.setMessage(message);

        HeaderDigest[] headerDigests = new HeaderDigest[] {consensusDigest};
        var optResult = DigestHelper.getBabePreRuntimeDigest(headerDigests);

        assertTrue(optResult.isPresent());

        var result = optResult.get();
        assertEquals(PreDigestType.BABE_PRIMARY, result.getType());
        assertEquals(0, result.getAuthorityIndex());
        assertEquals(BigInteger.ZERO, result.getSlotNumber());
        assertArrayEquals(new byte[32], result.getVrfOutput());
        assertArrayEquals(new byte[64], result.getVrfProof());
    }

    @Test
    void getBabePreRuntimeDigestForSecondaryPlainSlotTest() {
        HeaderDigest consensusDigest = new HeaderDigest();
        consensusDigest.setId(ConsensusEngine.BABE);
        consensusDigest.setType(DigestType.PRE_RUNTIME);

        // Create a PreRuntimeDigest with:
        // Type -> BABE_SECONDARY_PLAIN(2)
        // Authority index -> 0
        // Slot number -> 0
        var message = new byte[97];
        message[0] = 2;
        consensusDigest.setMessage(message);

        HeaderDigest[] headerDigests = new HeaderDigest[] {consensusDigest};
        var optResult = DigestHelper.getBabePreRuntimeDigest(headerDigests);

        assertTrue(optResult.isPresent());

        var result = optResult.get();
        assertEquals(PreDigestType.BABE_SECONDARY_PLAIN, result.getType());
        assertEquals(0, result.getAuthorityIndex());
        assertEquals(BigInteger.ZERO, result.getSlotNumber());
        assertNull(result.getVrfOutput());
        assertNull(result.getVrfProof());
    }

    @Test
    void getBabePreRuntimeDigestWithoutSuchDigestInHeadersTest() {
        var optResult = DigestHelper.getBabePreRuntimeDigest(new HeaderDigest[0]);
        assertTrue(optResult.isEmpty());
    }

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

        HeaderDigest sealHeaderDigest = DigestHelper.buildSealHeaderDigest(blockHeader, keyPair);

        assertNotNull(sealHeaderDigest);
        assertEquals(DigestType.SEAL, sealHeaderDigest.getType());
        assertEquals(ConsensusEngine.BABE, sealHeaderDigest.getId());
    }
}
