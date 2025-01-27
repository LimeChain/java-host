package com.limechain.state;

import com.limechain.ServiceState;
import com.limechain.sync.SyncMode;
import lombok.Getter;
import org.javatuples.Pair;

public abstract class AbstractState implements ServiceState {

    @Getter
    protected boolean initialized;

    @Getter
    private static SyncMode syncMode;
    @Getter
    private static boolean isActiveAuthority = false;
    @Getter
    private static Pair<byte[], byte[]> grandpaKeyPair = null;

    public static void setSyncMode(SyncMode mode) {
        if (syncMode != null && syncMode.ordinal() > mode.ordinal()) {
            throw new IllegalStateException(mode + " mode precedes " + syncMode);
        }

        syncMode = mode;
    }

    public static void setAuthorityStatus(Pair<byte[], byte[]> keyPair) {
        isActiveAuthority = true;
        grandpaKeyPair = keyPair;
    }

    public static void unsetAuthorityStatus() {
        isActiveAuthority = false;
        grandpaKeyPair = null;
    }

    @Override
    public void initialize() {
        initialized = true;
    }

    @Override
    public void initializeFromDatabase() {
        //Do nothing
    }

    @Override
    public void persistState() {
        //Do nothing
    }
}
