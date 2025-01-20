package com.limechain.sync.warpsync.action;

import com.limechain.exception.sync.JustificationVerificationException;
import com.limechain.network.protocol.warp.DigestHelper;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.WarpSyncFragment;
import com.limechain.rpc.server.AppBean;
import com.limechain.state.StateManager;
import com.limechain.sync.JustificationVerifier;
import com.limechain.sync.state.SyncState;
import com.limechain.sync.warpsync.WarpSyncMachine;
import com.limechain.sync.warpsync.WarpSyncState;
import lombok.extern.java.Log;

import java.util.logging.Level;

// VerifyJustificationState is going to be instantiated a lot of times
// Maybe we can make it a singleton in order to reduce performance overhead?
@Log
public class VerifyJustificationAction implements WarpSyncAction {

    private final WarpSyncState warpSyncState;
    private final StateManager stateManager;
    private Exception error;

    public VerifyJustificationAction() {
        this.stateManager = AppBean.getBean(StateManager.class);
        this.warpSyncState = AppBean.getBean(WarpSyncState.class);
    }

    @Override
    public void next(WarpSyncMachine sync) {
        if (this.error != null) {
            // Not sure what state we should transition to here.
            sync.setWarpSyncAction(new FinishedAction());
            return;
        }

        if (!sync.getFragmentsQueue().isEmpty()) {
            sync.setWarpSyncAction(new VerifyJustificationAction());
        } else if (warpSyncState.isWarpSyncFragmentsFinished()) {
            sync.setWarpSyncAction(new RuntimeDownloadAction());
        } else {
            sync.setWarpSyncAction(new RequestFragmentsAction(stateManager.getSyncState().getLastFinalizedBlockHash()));
        }
    }

    @Override
    public void handle(WarpSyncMachine sync) {
        try {
            warpSyncState.handleScheduledEvents();

            WarpSyncFragment fragment = sync.getFragmentsQueue().poll();
            log.log(Level.INFO, "Verifying justification...");
            if (fragment == null) {
                throw new JustificationVerificationException("No such fragment");
            }
            boolean verified = JustificationVerifier.verify(
                    fragment.getJustification().getPrecommits(),
                    fragment.getJustification().getRound());
            if (!verified) {
                throw new JustificationVerificationException("Justification could not be verified.");
            }

            stateManager.getSyncState().finalizeHeader(fragment.getHeader());
            handleAuthorityChanges(fragment);
        } catch (Exception e) {
            log.log(Level.WARNING, "Error while verifying justification: " + e.getMessage());
            this.error = e;
        }
    }

    private void handleAuthorityChanges(WarpSyncFragment fragment) {
        BlockHeader header = fragment.getHeader();

        DigestHelper.getGrandpaConsensusMessage(header.getDigest())
                .ifPresent(cm -> stateManager.getGrandpaSetState()
                        .handleGrandpaConsensusMessage(cm, header.getBlockNumber()));

        SyncState syncState = stateManager.getSyncState();
        log.log(Level.INFO, "Verified justification. Block hash is now at #"
                + syncState.getLastFinalizedBlockNumber() + ": "
                + syncState.getLastFinalizedBlockHash().toString()
                + " with state root " + syncState.getStateRoot());
    }
}
