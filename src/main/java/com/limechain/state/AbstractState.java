package com.limechain.state;

import com.limechain.ServiceState;
import com.limechain.sync.SyncMode;
import lombok.Getter;

public abstract class AbstractState implements ServiceState {

    @Getter
    private static SyncMode syncMode;
    @Getter
    protected boolean initialized;

    public static void setSyncMode(SyncMode mode) {
        if (syncMode.ordinal() > mode.ordinal()) {
            throw new IllegalStateException(mode + " mode precedes " + syncMode);
        }

        syncMode = mode;
    }
}
