package com.limechain.sync.warpsync.action;

import com.limechain.exception.global.RuntimeCodeException;
import com.limechain.rpc.server.AppBean;
import com.limechain.sync.state.SyncState;
import com.limechain.sync.warpsync.WarpSyncMachine;
import com.limechain.sync.warpsync.WarpSyncState;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;

import java.util.logging.Level;

@Log
@AllArgsConstructor
public class RuntimeDownloadAction implements WarpSyncAction {
    private final WarpSyncState warpSyncState;
    private final SyncState syncState;
    private Exception error;

    public RuntimeDownloadAction() {
        this.warpSyncState = AppBean.getBean(WarpSyncState.class);
        this.syncState = AppBean.getBean(SyncState.class);
    }

    @Override
    public void next(WarpSyncMachine sync) {
        if (this.error != null) {
            log.log(Level.SEVERE, "Error occurred during runtime download state: " + this.error.getMessage());
            sync.setWarpSyncAction(new RequestFragmentsAction(syncState.getLastFinalizedBlockHash()));
            return;
        }
        // After runtime is downloaded, we have to build the runtime and then build chain information
        sync.setWarpSyncAction(new RuntimeBuildAction());
    }

    @Override
    public void handle(WarpSyncMachine sync) {
        try {
            log.log(Level.INFO, "Loading saved runtime...");
            warpSyncState.loadSavedRuntimeCode();
        } catch (RuntimeCodeException e) {
            handleDownloadRuntime();
        }
    }

    private void handleDownloadRuntime() {
        try {
            log.log(Level.INFO, "Downloading runtime...");
            warpSyncState.updateRuntimeCode();
        } catch (RuntimeCodeException e) {
            this.error = e;
        }
    }
}
