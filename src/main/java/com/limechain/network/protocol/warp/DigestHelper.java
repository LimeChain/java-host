package com.limechain.network.protocol.warp;

import com.limechain.babe.consensus.BabeConsensusMessage;
import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.consensus.scale.BabeConsensusMessageReader;
import com.limechain.babe.predigest.scale.PreDigestReader;
import com.limechain.network.protocol.warp.dto.ConsensusEngine;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.utils.scale.ScaleUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * Helper class for processing different types of header digests
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DigestHelper {

    public static Optional<BabeConsensusMessage> getBabeConsensusMessage(HeaderDigest[] headerDigests) {
        return Arrays.stream(headerDigests)
                .filter(headerDigest -> DigestType.CONSENSUS_MESSAGE.equals(headerDigest.getType()) &&
                        ConsensusEngine.BABE.equals(headerDigest.getId()))
                .findFirst()
                .map(HeaderDigest::getMessage)
                .map(message -> ScaleUtils.Decode.decode(message, new BabeConsensusMessageReader()));
    }

    public static Optional<BabePreDigest> getBabePreRuntimeDigest(HeaderDigest[] headerDigests) {
        return Arrays.stream(headerDigests)
                .filter(headerDigest -> DigestType.PRE_RUNTIME.equals(headerDigest.getType()) &&
                        ConsensusEngine.BABE.equals(headerDigest.getId()))
                .findFirst()
                .map(HeaderDigest::getMessage)
                .map(message -> ScaleUtils.Decode.decode(message, new PreDigestReader()));
    }
}
