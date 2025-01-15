package com.limechain.sync;

/**
 * Each mode shows the current state of the syncing process. For example "HEAD" shows that the node is at the head of
 * the chain and is syncing via network messages. Different network modes affect how various business logic is executed.
 */
public enum SyncMode {
    /**
     * Shows that either {@link com.limechain.sync.warpsync.WarpSyncMachine} is to follow or is running.
     */
    WARP,
    /**
     * Shows that either {@link com.limechain.sync.fullsync.FullSyncMachine} is to follow or is running.
     */
    FULL,
    /**
     * Shows that the node is at the head of the chain. This means one of two things:<br>
     * - If the node is a full node it passively syncs with peers via protocol messages.<br>
     * - If the node is an authoring node it both syncs with peers and participates in consensus algorithms.
     */
    HEAD
}
