package com.limechain.client;

import com.limechain.grandpa.state.RoundState;
import com.limechain.network.NetworkService;
import com.limechain.rpc.server.AppBean;
import com.limechain.sync.SyncService;
import com.limechain.sync.state.SyncState;

import java.util.List;
import java.util.Objects;

/**
 * Main light client class that starts and stops execution of
 * the client and hold references to dependencies
 */
public class LightClient extends HostNode {

    public LightClient() {
        super(List.of(
                        Objects.requireNonNull(AppBean.getBean(NetworkService.class)),
                        Objects.requireNonNull(AppBean.getBean(SyncService.class))),
                List.of(
                        Objects.requireNonNull(AppBean.getBean(SyncState.class))));
    }
}
