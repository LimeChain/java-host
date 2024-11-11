package com.limechain.rpc.config;

import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImplExporter;
import com.limechain.chain.ChainService;
import com.limechain.cli.Cli;
import com.limechain.cli.CliArguments;
import com.limechain.config.HostConfig;
import com.limechain.config.SystemInfo;
import com.limechain.constants.GenesisBlockHash;
import com.limechain.network.Network;
import com.limechain.network.request.ProtocolRequester;
import com.limechain.rpc.server.UnsafeInterceptor;
import com.limechain.runtime.RuntimeBuilder;
import com.limechain.storage.DBInitializer;
import com.limechain.storage.KVRepository;
import com.limechain.storage.block.BlockHandler;
import com.limechain.storage.block.SyncState;
import com.limechain.storage.trie.TrieStorage;
import com.limechain.sync.fullsync.FullSyncMachine;
import com.limechain.sync.warpsync.WarpSyncMachine;
import com.limechain.sync.warpsync.WarpSyncState;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * Spring configuration class used to instantiate beans.
 */
@Configuration
@EnableScheduling
public class CommonConfig {
    @Bean
    public static AutoJsonRpcServiceImplExporter autoJsonRpcServiceImplExporter() {
        final var jsonService = new AutoJsonRpcServiceImplExporter();

        jsonService.setInterceptorList(List.of(new UnsafeInterceptor()));
        jsonService.setAllowLessParams(true);

        return jsonService;
    }

    @Bean
    public CliArguments cliArgs(ApplicationArguments arguments) {
        return new Cli().parseArgs(arguments.getSourceArgs());
    }

    @Bean
    public HostConfig hostConfig(CliArguments cliArgs) {
        return new HostConfig(cliArgs);
    }

    @Bean
    public KVRepository<String, Object> repository(HostConfig hostConfig) {
        return DBInitializer.initialize(hostConfig.getRocksDbPath(),
                hostConfig.getChain(), hostConfig.isDbRecreate());
    }

    @Bean
    public TrieStorage trieStorage(KVRepository<String, Object> repository) {
        return new TrieStorage(repository);
    }

    @Bean
    public ChainService chainService(HostConfig hostConfig, KVRepository<String, Object> repository) {
        return new ChainService(hostConfig, repository);
    }

    @Bean
    public SyncState syncState(GenesisBlockHash genesisBlockHash, KVRepository<String, Object> repository) {
        return new SyncState(genesisBlockHash, repository);
    }

    @Bean
    public SystemInfo systemInfo(HostConfig hostConfig, Network network, SyncState syncState) {
        return new SystemInfo(hostConfig, network, syncState);
    }

    @Bean
    public Network network(ChainService chainService, HostConfig hostConfig, KVRepository<String, Object> repository,
                           CliArguments cliArgs, GenesisBlockHash genesisBlockHash) {
        return new Network(chainService, hostConfig, repository, cliArgs, genesisBlockHash);
    }

    @Bean
    public WarpSyncState warpSyncState(Network network, SyncState syncState,
                                       KVRepository<String, Object> repository,
                                       RuntimeBuilder runtimeBuilder,
                                       ProtocolRequester requester) {
        return new WarpSyncState(syncState, network, repository, runtimeBuilder, requester);
    }

    @Bean
    public WarpSyncMachine warpSyncMachine(Network network, ChainService chainService, SyncState syncState,
                                           WarpSyncState warpSyncState) {
        return new WarpSyncMachine(network, chainService, syncState, warpSyncState);
    }

    @Bean
    public FullSyncMachine fullSyncMachine(HostConfig hostConfig, Network network,
                                           SyncState syncState,
                                           ProtocolRequester requester,
                                           BlockHandler blockHandler) {
        return new FullSyncMachine(hostConfig, network, syncState, requester, blockHandler);
    }

    @Bean
    public GenesisBlockHash genesisBlockHash(ChainService chainService) {
        return new GenesisBlockHash(chainService);
    }

}
