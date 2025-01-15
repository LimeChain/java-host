package com.limechain.network.protocol.blockannounce.messages;

import com.limechain.config.HostConfig;
import com.limechain.network.protocol.blockannounce.NodeRole;
import com.limechain.rpc.server.AppBean;
import com.limechain.sync.state.SyncState;
import io.emeraldpay.polkaj.types.Hash256;

import java.math.BigInteger;

public class BlockAnnounceHandshakeBuilder {

    /**
     * Creates a Block Announce handshake based on the latest finalized Host state
     *
     * @return our Block Announce handshake
     */
    public BlockAnnounceHandshake getBlockAnnounceHandshake() {
        SyncState syncState = AppBean.getBean(SyncState.class);
        HostConfig hostConfig = AppBean.getBean(HostConfig.class);

        Hash256 genesisBlockHash = syncState.getGenesisBlockHash();
        Hash256 lastFinalizedBlockHash = syncState.getLastFinalizedBlockHash();
        BigInteger lastFinalizedBlockNumber = syncState.getLastFinalizedBlockNumber();

        NodeRole nodeRole = hostConfig.getNodeRole();

        Hash256 blockHash = lastFinalizedBlockHash == null
                ? genesisBlockHash
                : lastFinalizedBlockHash;
        return new BlockAnnounceHandshake(
                nodeRole.getValue(),
                lastFinalizedBlockNumber,
                blockHash,
                genesisBlockHash
        );
    }

}
