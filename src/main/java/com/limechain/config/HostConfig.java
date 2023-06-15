package com.limechain.config;

import com.limechain.chain.Chain;
import com.limechain.cli.CliArguments;
import com.limechain.constants.RpcConstants;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Value;

import java.util.logging.Level;

import static com.limechain.chain.Chain.WESTEND;
import static com.limechain.chain.Chain.fromString;

/**
 * Configuration class used to store any Host specific information
 */
@Log
@Getter
@Setter
public class HostConfig {
    /**
     * File path under which the DB will be created
     */
    private String rocksDbPath;

    /**
     * Chain the Host is running on
     */
    private Chain chain;
    private String rpcNodeAddress;
    @Value("${genesis.path.polkadot}")
    private String polkadotGenesisPath;
    @Value("${genesis.path.kusama}")
    private String kusamaGenesisPath;
    @Value("${genesis.path.westend}")
    private String westendGenesisPath;
    @Value("${genesis.path.local}")
    private String localGenesisPath;

    public HostConfig(CliArguments cliArguments) {
        this.setRocksDbPath(cliArguments.dbPath());
        String network = cliArguments.network();
        this.setChain(network.isEmpty() ? WESTEND : fromString(network));
        if (chain == null) {
            throw new RuntimeException("Unsupported or unknown network");
        }
        log.log(Level.INFO, String.format("✅️Loaded app config for chain %s%n", chain));
        switch (this.getChain()) {
            case POLKADOT, LOCAL -> {
                this.setRpcNodeAddress(RpcConstants.POLKADOT_WS_RPC);
            }
            case KUSAMA -> {
                this.setRpcNodeAddress(RpcConstants.KUSAMA_WS_RPC);
            }
            case WESTEND -> {
                this.setRpcNodeAddress(RpcConstants.WESTEND_WS_RPC);
            }
        }
    }

    /**
     * Gets the genesis file path based on the chain the node is configured
     *
     * @return genesis(chain spec) file path
     * @throws RuntimeException if chain is invalid.
     *                          This shouldn't be possible in practice because of preceding validations.
     */
    public String getGenesisPath() {
        switch (chain) {
            case POLKADOT -> {
                return polkadotGenesisPath;
            }
            case KUSAMA -> {
                return kusamaGenesisPath;
            }
            case WESTEND -> {
                return westendGenesisPath;
            }
            case LOCAL -> {
                return localGenesisPath;
            }
            default -> {
                throw new RuntimeException("Invalid Chain in host configuration");
            }
        }
    }
}
