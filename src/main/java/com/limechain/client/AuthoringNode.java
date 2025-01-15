package com.limechain.client;

import com.limechain.babe.state.EpochState;
import com.limechain.grandpa.state.RoundState;
import com.limechain.network.NetworkService;
import com.limechain.rpc.server.AppBean;
import com.limechain.storage.block.state.BlockState;
import com.limechain.sync.SyncService;
import com.limechain.sync.state.SyncState;
import com.limechain.transaction.TransactionState;

import java.util.List;
import java.util.Objects;

public class AuthoringNode extends HostNode {

    public AuthoringNode() {
        super(List.of(
                //TODO Add babe and grandpa services.
                        Objects.requireNonNull(AppBean.getBean(NetworkService.class)),
                        Objects.requireNonNull(AppBean.getBean(SyncService.class))),
                List.of(
                        Objects.requireNonNull(AppBean.getBean(BlockState.class)),
                        Objects.requireNonNull(AppBean.getBean(SyncState.class)),
                        Objects.requireNonNull(AppBean.getBean(EpochState.class)),
                        Objects.requireNonNull(AppBean.getBean(RoundState.class)),
                        Objects.requireNonNull(AppBean.getBean(TransactionState.class))));
    }
}
