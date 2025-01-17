package com.limechain.sync.warpsync;

import com.limechain.chain.ChainService;
import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.chain.lightsyncstate.LightSyncState;
import com.limechain.grandpa.state.RoundState;
import com.limechain.network.NetworkService;
import com.limechain.network.protocol.warp.dto.WarpSyncFragment;
import com.limechain.state.StateManager;
import com.limechain.storage.block.state.BlockState;
import com.limechain.sync.state.SyncState;
import com.limechain.sync.warpsync.action.FinishedAction;
import com.limechain.sync.warpsync.action.RequestFragmentsAction;
import com.limechain.sync.warpsync.action.WarpSyncAction;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import org.javatuples.Pair;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

@Log
@Getter
@Setter
public class WarpSyncMachine {

    private final PriorityQueue<Pair<BigInteger, Authority[]>> scheduledAuthorityChanges;
    private final ChainInformation chainInformation;
    private Queue<WarpSyncFragment> fragmentsQueue;
    private final ChainService chainService;
    private final ExecutorService executor;
    private WarpSyncAction warpSyncAction;
    private final WarpSyncState warpState;
    private final StateManager stateManager;
    private final NetworkService networkService;
    private final List<Runnable> onFinishCallbacks;

    public WarpSyncMachine(NetworkService network,
                           ChainService chainService,
                           WarpSyncState warpSyncState,
                           StateManager stateManager) {
        this.networkService = network;
        this.chainService = chainService;
        this.warpState = warpSyncState;
        this.stateManager = stateManager;
        this.executor = Executors.newSingleThreadExecutor();
        this.scheduledAuthorityChanges = new PriorityQueue<>(Comparator.comparing(Pair::getValue0));
        this.chainInformation = new ChainInformation();
        this.onFinishCallbacks = new ArrayList<>();
    }

    public void nextState() {
        warpSyncAction.next(this);
    }

    public void handleState() {
        warpSyncAction.handle(this);
    }

    public boolean isSyncing() {
        return this.warpSyncAction.getClass() != FinishedAction.class;
    }

    public void start() {
        SyncState syncState = stateManager.getSyncState();
        RoundState roundState = stateManager.getRoundState();

        if (this.chainService.getChainSpec().getLightSyncState() != null) {
            LightSyncState initState = LightSyncState.decode(this.chainService.getChainSpec().getLightSyncState());
            if (syncState.getLastFinalizedBlockNumber()
                    .compareTo(initState.getFinalizedBlockHeader().getBlockNumber()) < 0) {
                syncState.setLightSyncState(initState);
                roundState.setLightSyncState(initState);
            }
        }
        final Hash256 initStateHash = syncState.getLastFinalizedBlockHash();

        // Always start with requesting fragments
        log.log(Level.INFO, "Requesting fragments...");
        this.warpSyncAction = new RequestFragmentsAction(initStateHash);

        executor.submit(() -> {
            while (this.warpSyncAction.getClass() != FinishedAction.class) {
                this.handleState();
                this.nextState();
            }

            finishWarpSync();
        });
    }

    public void stop() {
        log.info("Stopping warp sync machine");
        executor.shutdown();
        this.warpSyncAction = null;
        log.info("Warp sync machine stopped.");
    }

    private void finishWarpSync() {
        SyncState syncState = stateManager.getSyncState();
        RoundState roundState = stateManager.getRoundState();
        BlockState blockState = stateManager.getBlockState();

        this.warpState.setWarpSyncFinished(true);
        syncState.persistState();
        roundState.persistState();

        blockState.setupPostWarpSync(syncState.getLastFinalizedBlockHash(), syncState.getLastFinalizedBlockNumber());

        log.info("Warp sync finished.");
        this.onFinishCallbacks.forEach(executor::submit);
    }

    public void onFinish(Runnable... function) {
        onFinishCallbacks.addAll(List.of(function));
    }
}
