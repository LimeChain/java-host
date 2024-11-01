package com.limechain.network.protocol.warp;

import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.predigest.scale.PreDigestReader;
import com.limechain.babe.state.EpochState;
import com.limechain.network.protocol.warp.dto.ConsensusEngine;
import com.limechain.network.protocol.warp.dto.DigestType;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.rpc.server.AppBean;
import com.limechain.utils.scale.ScaleUtils;

import java.util.Arrays;
import java.util.Optional;

/**
 * Helper class for processing different types of header digests
 */
public class DigestHelper {
    private EpochState epochState;

    public DigestHelper() {
        this.epochState = AppBean.getBean(EpochState.class);
    }

    public void handleHeaderDigests(HeaderDigest[] headerDigests) {
        if (headerDigests == null || headerDigests.length == 0) {
            return;
        }
        updateEpochStateIfBabeConsensusMessageExists(headerDigests);
        handleBabePreRuntimeDigest(headerDigests);
    }

    private void updateEpochStateIfBabeConsensusMessageExists(HeaderDigest[] headerDigests) {
        Arrays.stream(headerDigests)
                .filter(headerDigest -> DigestType.CONSENSUS_MESSAGE.equals(headerDigest.getType()) &&
                        ConsensusEngine.BABE.equals(headerDigest.getId()))
                .findFirst()
                .map(HeaderDigest::getMessage)
                .ifPresent(message -> epochState.updateNextEpochBlockConfig(message));
    }

    private Optional<BabePreDigest> handleBabePreRuntimeDigest(HeaderDigest[] headerDigests) {
        return Arrays.stream(headerDigests)
                .filter(headerDigest -> DigestType.PRE_RUNTIME.equals(headerDigest.getType()) &&
                        ConsensusEngine.BABE.equals(headerDigest.getId()))
                .findFirst()
                .map(HeaderDigest::getMessage)
                .map(message -> ScaleUtils.Decode.decode(message, new PreDigestReader()));
    }
}
