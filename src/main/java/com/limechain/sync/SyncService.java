package com.limechain.sync;

import com.limechain.NodeService;
import com.limechain.cli.CliArguments;
import com.limechain.network.PeerMessageCoordinator;
import com.limechain.network.protocol.blockannounce.NodeRole;
import com.limechain.state.AbstractState;
import com.limechain.sync.fullsync.FullSyncMachine;
import com.limechain.sync.warpsync.WarpSyncMachine;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SyncService implements NodeService {

    private final WarpSyncMachine warpSyncMachine;
    private final FullSyncMachine fullSyncMachine;
    private final PeerMessageCoordinator messageCoordinator;
    private final CliArguments arguments;

    @Override
    public void start() {
        SyncMode initSyncMode = arguments.syncMode();
        AbstractState.setSyncMode(initSyncMode);

        switch (NodeRole.fromString(arguments.nodeRole())) {
            case LIGHT -> {
                warpSyncMachine.onFinish(() -> {
                    AbstractState.setSyncMode(SyncMode.HEAD);
                    messageCoordinator.handshakeBootNodes();
                    messageCoordinator.handshakePeers();
                });
                warpSyncMachine.start();
            }
            case FULL, AUTHORING -> {
                switch (initSyncMode) {
                    case FULL -> fullSyncMachine.start();
                    case WARP -> {
                        warpSyncMachine.onFinish(() -> AbstractState.setSyncMode(SyncMode.FULL),
                                fullSyncMachine::start);
                        warpSyncMachine.start();
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + arguments.syncMode());
                }
            }
            case null -> throw new IllegalStateException("Node role should not be null.");
            default -> throw new IllegalStateException("Unexpected node role: " + arguments.nodeRole());
        }
    }

    @Override
    @PreDestroy
    public void stop() {
        warpSyncMachine.stop();
    }
}
