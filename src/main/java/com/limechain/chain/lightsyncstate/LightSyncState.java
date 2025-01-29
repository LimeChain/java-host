package com.limechain.chain.lightsyncstate;

import com.limechain.chain.lightsyncstate.scale.AuthoritySetReader;
import com.limechain.chain.lightsyncstate.scale.EpochChangesReader;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.scale.reader.BlockHeaderReader;
import com.limechain.utils.StringUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import lombok.Getter;

import java.util.Map;

@Getter
public class LightSyncState {
    private BlockHeader finalizedBlockHeader;
    private EpochChanges epochChanges;
    private AuthoritySet grandpaAuthoritySet;

    public static LightSyncState decode(Map<String, String> lightSyncState) {
        String header = lightSyncState.get("finalizedBlockHeader");
        String epochChanges = lightSyncState.get("babeEpochChanges");
        String grandpaAuthoritySet = lightSyncState.get("grandpaAuthoritySet");

        if (header == null) {
            throw new IllegalStateException("finalizedBlockHeader is null");
        }
        if (epochChanges == null) {
            throw new IllegalStateException("epochChanges is null");
        }
        if (grandpaAuthoritySet == null) {
            throw new IllegalStateException("grandpaAuthoritySet is null");
        }

        var state = new LightSyncState();
        state.finalizedBlockHeader = BlockHeaderReader.getInstance()
                .read(new ScaleCodecReader(StringUtils.hexToBytes(header)));

        state.epochChanges = EpochChangesReader.getInstance()
                .read(new ScaleCodecReader(StringUtils.hexToBytes(epochChanges)));

        state.grandpaAuthoritySet = AuthoritySetReader.getInstance()
                .read(new ScaleCodecReader(StringUtils.hexToBytes(grandpaAuthoritySet)));

        return state;
    }
}
