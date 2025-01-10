package com.limechain.sync.warpsync;

import com.limechain.chain.ChainService;
import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.chain.lightsyncstate.LightSyncState;
import com.limechain.grandpa.state.RoundState;
import com.limechain.network.Network;
import com.limechain.network.protocol.warp.dto.WarpSyncFragment;
import com.limechain.storage.block.BlockState;
import com.limechain.storage.block.SyncState;
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
    private final Network networkService;
    private final SyncState syncState;
    private final RoundState roundState;
    private final List<Runnable> onFinishCallbacks;

    public WarpSyncMachine(Network network, ChainService chainService, SyncState syncState, WarpSyncState warpSyncState, RoundState roundState) {
        this.networkService = network;
        this.chainService = chainService;
        this.syncState = syncState;
        this.warpState = warpSyncState;
        this.roundState = roundState;
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
        if (this.chainService.getChainSpec().getLightSyncState() != null) {
            LightSyncState initState = LightSyncState.decode(this.chainService.getChainSpec().getLightSyncState());
            if (this.syncState.getLastFinalizedBlockNumber()
                        .compareTo(initState.getFinalizedBlockHeader().getBlockNumber()) < 0) {
                this.syncState.setLightSyncState(initState);
                this.roundState.setLightSyncState(initState);
            }
        }
        final Hash256 initStateHash = this.syncState.getLastFinalizedBlockHash();

        // Always start with requesting fragments
        log.log(Level.INFO, "Requesting fragments...");
        this.networkService.updateCurrentSelectedPeerWithNextBootnode();
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
        this.warpState.setWarpSyncFinished(true);
        this.syncState.persistState();
        this.roundState.persistState();

        BlockState.getInstance().initializeAfterWarpSync(
                syncState.getLastFinalizedBlockHash(),
                syncState.getLastFinalizedBlockNumber()
        );

        log.info("Warp sync finished.");
        this.onFinishCallbacks.forEach(executor::submit);
    }

    public void onFinish(Runnable function) {
        onFinishCallbacks.add(function);
    }
}
