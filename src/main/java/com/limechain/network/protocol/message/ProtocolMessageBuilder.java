package com.limechain.network.protocol.message;

import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.grandpa.messages.neighbour.NeighbourMessage;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.rpc.server.AppBean;
import com.limechain.storage.block.SyncState;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ProtocolMessageBuilder {
    private final int NEIGHBOUR_MESSAGE_VERSION = 1;

    public NeighbourMessage buildNeighbourMessage() {
        SyncState syncState = AppBean.getBean(SyncState.class);

        return new NeighbourMessage(
                NEIGHBOUR_MESSAGE_VERSION,
                syncState.getLatestRound(),
                syncState.getSetId(),
                syncState.getLastFinalizedBlockNumber()
        );
    }

    public BlockAnnounceMessage buildBlockAnnounceMessage(BlockHeader blockHeader, boolean isBestBlock) {
        return new BlockAnnounceMessage(
                blockHeader,
                isBestBlock
        );
    }
}
