package com.limechain.runtime;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Used to identify runtime API endpoints by their names as listed in the spec.
 * @see <a href="https://spec.polkadot.network/chap-runtime-api">the spec</a>
 */
// NOTE: Add here whatever endpoints necessary during development.
@Getter
@AllArgsConstructor
public enum RuntimeEndpoint {
    CORE_EXECUTE_BLOCK("Core_execute_block"),
    CORE_VERSION("Core_version"),
    BABE_API_CONFIGURATION("BabeApi_configuration"),
    BABE_API_CURRENT_EPOCH("BabeApi_current_epoch"),
    BABE_API_CURRENT_EPOCH_START("BabeApi_current_epoch_start"),
    BLOCKBUILDER_CHECK_INHERENTS("BlockBuilder_check_inherents"),
    METADATA_METADATA("Metadata_metadata"),
    SESSION_KEYS_GENERATE_SESSION_KEYS("SessionKeys_generate_session_keys"),
    SESSION_KEYS_DECODE_SESSION_KEYS("SessionKeys_decode_session_keys");

    private final String name;
}
