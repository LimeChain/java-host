package com.limechain.network.protocol.message;

import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.grandpa.messages.neighbour.NeighbourMessage;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.rpc.server.AppBean;
import com.limechain.storage.block.SyncState;


public class ProtocolMessageBuilder {
    private static final int NEIGHBOUR_MESSAGE_VERSION = 1;

    private final SyncState syncState;

    public ProtocolMessageBuilder() {
        this.syncState = AppBean.getBean(SyncState.class);
    }

    public NeighbourMessage buildNeighbourMessage() {
        return new NeighbourMessage(
                NEIGHBOUR_MESSAGE_VERSION,
                syncState.getLatestRound(),
                syncState.getSetId(),
                syncState.getLastFinalizedBlockNumber()
        );
    }

    public static BlockAnnounceMessage buildBlockAnnounceMessage(BlockHeader blockHeader, boolean isBestBlock) {
        return new BlockAnnounceMessage(
                blockHeader,
                isBestBlock
        );
    }
}
