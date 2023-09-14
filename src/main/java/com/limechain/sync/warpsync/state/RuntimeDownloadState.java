package com.limechain.sync.warpsync.state;

import com.limechain.sync.warpsync.SyncedState;
import com.limechain.sync.warpsync.WarpSyncMachine;
import com.limechain.sync.warpsync.dto.RuntimeCodeException;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;

import java.util.logging.Level;

@Log
@AllArgsConstructor
public class RuntimeDownloadState implements WarpSyncState {
    private final SyncedState syncedState;
    private Exception error;

    public RuntimeDownloadState() {
        this.syncedState = SyncedState.getInstance();
    }

    @Override
    public void next(WarpSyncMachine sync) {
        if (this.error != null) {
            log.log(Level.SEVERE, "Error occurred during runtime download state: " + this.error.getMessage());
            sync.setWarpSyncState(new RequestFragmentsState(syncedState.getLastFinalizedBlockHash()));
            return;
        }
        // After runtime is downloaded, we have to build the runtime and then build chain information
        sync.setWarpSyncState(new RuntimeBuildState());
    }

    @Override
    public void handle(WarpSyncMachine sync) {
        try {
            log.log(Level.INFO, "Loading saved runtime...");
            syncedState.loadSavedRuntimeCode();
        } catch (RuntimeCodeException e) {
            handleDownloadRuntime();
        }
    }

    private void handleDownloadRuntime() {
        try {
            log.log(Level.INFO, "Downloading runtime...");
            syncedState.updateRuntimeCode();
        } catch (RuntimeCodeException e) {
            this.error = e;
        }
    }
}
