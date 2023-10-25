package com.limechain.chain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

/**
 * Holds information parsed from the chain spec(genesis) file
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChainSpec implements Serializable {
    private String name;
    private String id;
    private String chainType;
    private String[] bootNodes;
    private Object[] telemetryEndpoints;
    private String protocolId;
    private Fields genesis;
    private Map<String, Object> properties;
    private String[] forkBlocks;
    private String[] badBlocks;
    private String consensusEngine;
    private Map<String, String> lightSyncState;

    /**
     * Loads chain specification data from json file and maps its fields
     *
     * @param pathToChainSpecJSON path to the chain specification json file
     * @return class instance mapped to the json file
     * @throws IOException If path is invalid
     */
    public static ChainSpec newFromJSON(String pathToChainSpecJSON) throws IOException {
        final boolean failOnUnknownProperties = false;
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties);
        var file = new File(pathToChainSpecJSON);

        return objectMapper.readValue(file, ChainSpec.class);
    }

    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Fields implements Serializable {
        public Map<String, Map<String, String>> raw;
        public Map<String, Map<String, Object>> runtime;
    }
}

