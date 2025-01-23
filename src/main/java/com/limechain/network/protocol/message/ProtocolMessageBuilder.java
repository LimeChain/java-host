package com.limechain.network.protocol.message;

import com.limechain.grandpa.state.GrandpaSetState;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.grandpa.messages.catchup.req.CatchUpReqMessage;
import com.limechain.network.protocol.grandpa.messages.neighbour.NeighbourMessage;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.rpc.server.AppBean;
import com.limechain.storage.block.SyncState;
import lombok.experimental.UtilityClass;

import java.math.BigInteger;

@UtilityClass
public class ProtocolMessageBuilder {
    private final int NEIGHBOUR_MESSAGE_VERSION = 1;

    public NeighbourMessage buildNeighbourMessage() {
        SyncState syncState = AppBean.getBean(SyncState.class);
        GrandpaSetState grandpaSetState = AppBean.getBean(GrandpaSetState.class);
        BigInteger setId = grandpaSetState.getSetId();

        return new NeighbourMessage(
                NEIGHBOUR_MESSAGE_VERSION,
                grandpaSetState.getRoundCache().getLatestRoundNumber(setId),
                setId,
                syncState.getLastFinalizedBlockNumber()
        );
    }

    public BlockAnnounceMessage buildBlockAnnounceMessage(BlockHeader blockHeader, boolean isBestBlock) {
        return new BlockAnnounceMessage(
                blockHeader,
                isBestBlock
        );
    }

    public CatchUpReqMessage buildCatchUpRequestMessage(GrandpaSetState grandpaSetState) {
        BigInteger setId = grandpaSetState.getSetId();
        BigInteger roundNumber = grandpaSetState.getRoundCache().getLatestRoundNumber(setId);

        return new CatchUpReqMessage(
                roundNumber,
                setId
        );
    }
}
