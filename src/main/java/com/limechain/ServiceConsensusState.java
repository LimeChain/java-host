package com.limechain;

import com.limechain.runtime.Runtime;

/**
 * This interface defines states that are initialized by runtime data and updated
 * via consensus messages (e.g. BABE, GRANDPA, BEEFY).
 *
 * <p>Implementing classes initialize state from runtime and update it with consensus messages.</p>
 */
public interface ServiceConsensusState {

    void populateDataFromRuntime(Runtime runtime);
}
