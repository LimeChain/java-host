package com.limechain.runtime;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Used to identify runtime API endpoints by their names as listed in the spec.
 *
 * @see <a href="https://spec.polkadot.network/chap-runtime-api">the spec</a>
 */
// NOTE: Add here whatever endpoints necessary during development.
@Getter
@AllArgsConstructor
public enum RuntimeEndpoint {
    CORE_VERSION("Core_version"),
    CORE_EXECUTE_BLOCK("Core_execute_block"),
    CORE_INITIALIZE_BLOCK("Core_initialize_block"),
    BABE_API_CONFIGURATION("BabeApi_configuration"),
    BABE_API_GENERATE_KEY_OWNERSHIP_PROOF("BabeApi_generate_key_ownership_proof"),
    BABE_API_SUBMIT_REPORT_EQUIVOCATION_UNSIGNED_EXTRINSIC("BabeApi_submit_report_equivocation_unsigned_extrinsic"),
    BLOCKBUILDER_FINALIZE_BLOCK("BlockBuilder_finalize_block"),
    BLOCKBUILDER_CHECK_INHERENTS("BlockBuilder_check_inherents"),
    BLOCKBUILDER_APPLY_EXTRINISIC("BlockBuilder_apply_extrinsic"),
    BLOCKBUILDER_INHERENT_EXTRINISICS("BlockBuilder_inherent_extrinisics"),
    METADATA_METADATA("Metadata_metadata"),
    SESSION_KEYS_GENERATE_SESSION_KEYS("SessionKeys_generate_session_keys"),
    SESSION_KEYS_DECODE_SESSION_KEYS("SessionKeys_decode_session_keys"),
    TRANSACTION_QUEUE_VALIDATE_TRANSACTION("TaggedTransactionQueue_validate_transaction"),
    ;

    private final String name;
}
