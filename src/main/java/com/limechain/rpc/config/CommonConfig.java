package com.limechain.rpc.config;

import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImplExporter;
import com.limechain.chain.ChainService;
import com.limechain.cli.Cli;
import com.limechain.cli.CliArguments;
import com.limechain.config.HostConfig;
import com.limechain.config.SystemInfo;
import com.limechain.constants.GenesisBlockHash;
import com.limechain.grandpa.state.RoundState;
import com.limechain.network.NetworkService;
import com.limechain.network.PeerMessageCoordinator;
import com.limechain.network.PeerRequester;
import com.limechain.rpc.server.UnsafeInterceptor;
import com.limechain.runtime.RuntimeBuilder;
import com.limechain.storage.DBInitializer;
import com.limechain.storage.KVRepository;
import com.limechain.storage.block.BlockHandler;
import com.limechain.storage.block.state.BlockState;
import com.limechain.storage.trie.TrieStorage;
import com.limechain.sync.SyncService;
import com.limechain.sync.fullsync.FullSyncMachine;
import com.limechain.sync.state.SyncState;
import com.limechain.sync.warpsync.WarpSyncMachine;
import com.limechain.sync.warpsync.WarpSyncState;
import com.limechain.transaction.TransactionState;
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
    public SystemInfo systemInfo(HostConfig hostConfig, NetworkService network, SyncState syncState) {
        return new SystemInfo(hostConfig, network, syncState);
    }

    @Bean
    public NetworkService networkService(ChainService chainService,
                                         HostConfig hostConfig,
                                         KVRepository<String, Object> repository,
                                         CliArguments cliArgs,
                                         GenesisBlockHash genesisBlockHash) {
        return new NetworkService(chainService, hostConfig, repository, cliArgs, genesisBlockHash);
    }

    @Bean
    public WarpSyncState warpSyncState(SyncState syncState,
                                       RoundState roundState,
                                       KVRepository<String, Object> repository,
                                       RuntimeBuilder runtimeBuilder,
                                       PeerRequester requester,
                                       PeerMessageCoordinator messageCoordinator) {
        return new WarpSyncState(syncState, repository, runtimeBuilder, requester, messageCoordinator, roundState);
    }

    @Bean
    public SyncService syncService(WarpSyncMachine warpSyncMachine,
                                   FullSyncMachine fullSyncMachine,
                                   PeerMessageCoordinator peerMessageCoordinator,
                                   CliArguments arguments) {
        return new SyncService(warpSyncMachine, fullSyncMachine, peerMessageCoordinator, arguments);
    }

    @Bean
    public WarpSyncMachine warpSyncMachine(NetworkService network,
                                           ChainService chainService,
                                           SyncState syncState,
                                           WarpSyncState warpSyncState,
                                           RoundState roundState,
                                           BlockState blockState) {
        return new WarpSyncMachine(network, chainService, syncState, warpSyncState, roundState, blockState);
    }

    @Bean
    public FullSyncMachine fullSyncMachine(HostConfig hostConfig, NetworkService network,
                                           SyncState syncState,
                                           BlockState blockState,
                                           TransactionState transactionState,
                                           PeerRequester requester,
                                           PeerMessageCoordinator coordinator,
                                           BlockHandler blockHandler) {
        return new FullSyncMachine(network, syncState, blockState, transactionState,
                requester, coordinator, blockHandler, hostConfig);
    }

    @Bean
    public GenesisBlockHash genesisBlockHash(ChainService chainService) {
        return new GenesisBlockHash(chainService);
    }

}
