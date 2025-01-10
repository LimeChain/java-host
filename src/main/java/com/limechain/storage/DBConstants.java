package com.limechain.storage;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DBConstants {
    /**
     * Key for storing the privateKey for nabu
     */
    public static final String PEER_ID = "nodePeerId";
    /**
     * Key under which the genesis chain spec is stored
     */
    public static final String GENESIS_KEY = "genesis";
    /**
     * Key under which the --latest-- state trie proof is stored
     * TODO: Currently only the latest loaded in sync is stored
     */
    public static final String RUNTIME_CODE = "runtimeCode";

    /**
     * Key under which the hash of the latest finalised block header is stored.
     */
    public static final String FINALIZED_BLOCK_KEY = "finalised_head";

    /**
     * Key under which the highest round and set id is stored.
     */
    public static final String HIGHEST_ROUND_AND_SET_ID_KEY = "hrs";


    // SyncState keys
    public static final String LAST_FINALIZED_BLOCK_NUMBER = "ss::lastFinalizedBlockNumber";
    public static final String LAST_FINALIZED_BLOCK_HASH = "ss::lastFinalizedBlockHash";
    public static final String STATE_ROOT = "ss::stateRoot";

    // GrandpaState keys
    public static final String AUTHORITY_SET = "gs::authoritySet";
    public static final String LATEST_ROUND = "gs::latestRound";
    public static final String SET_ID = "gs::setId";
    public static final String GRANDPA_PREVOTES = "gs:grandpaPrevotes";
    public static final String GRANDPA_PRECOMMITS = "gs:grandpaPrecommits";
}
