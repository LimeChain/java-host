package com.limechain.client;

import com.limechain.cli.CliArguments;
import com.limechain.constants.GenesisBlockHash;
import com.limechain.network.Network;
import com.limechain.rpc.server.AppBean;
import com.limechain.storage.KVRepository;
import com.limechain.storage.block.BlockState;
import com.limechain.storage.block.BlockStateHelper;
import com.limechain.storage.trie.TrieStorage;
import com.limechain.sync.fullsync.FullSyncMachine;
import com.limechain.sync.warpsync.WarpSyncMachine;
import com.limechain.trie.structure.TrieStructure;
import com.limechain.trie.structure.database.NodeData;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import java.math.BigInteger;
import java.util.logging.Level;

@Log
public abstract class AbstractNode implements HostNode{

    /**
     * Starts the light client by instantiating all dependencies and services
     *
     * @implNote the RpcApp is assumed to have been started before starting the client,
     * as it relies on the application context
     */
    @SneakyThrows
    public void start() {
        //Initialize state
        initializeGenesis();

        // Start network
        final Network network = AppBean.getBean(Network.class);
        network.start();

        // Wait for peers
        while (true) {
            if (!network.getKademliaService().getBootNodePeerIds().isEmpty()) {
                if (network.getKademliaService().getSuccessfulBootNodes() > 0) {
                    break;
                }
                network.updateCurrentSelectedPeer();
            }

            log.log(Level.INFO, "Waiting for peer connection...");
            Thread.sleep(10000); // TODO: Maybe extract this number into an application.property as it's duplicated
        }

        // Start syncing
        log.log(Level.INFO, "Node successfully connected to a peer! Sync can start!");

        CliArguments args = AppBean.getBean(CliArguments.class);
        WarpSyncMachine warpSyncMachine = AppBean.getBean(WarpSyncMachine.class);
        FullSyncMachine fullSyncMachine = AppBean.getBean(FullSyncMachine.class);

        switch (args.syncMode()) {
            case FULL -> fullSyncMachine.start();
            case WARP -> {
                warpSyncMachine.onFinish(fullSyncMachine::start);
                warpSyncMachine.start();
            }
            default -> throw new IllegalStateException("Unexpected value: " + args.syncMode());
        }
    }

    public static void initializeGenesis() {
        // TODO: Is there a better way to decide whether we've got any database written?
        KVRepository<String, Object> db = AppBean.getBean(KVRepository.class); //presume this works

        if (db == null) {
            throw new IllegalStateException("Database is not initialized");
        }
        TrieStorage trieStorage = AppBean.getBean(TrieStorage.class);
        // if: database has some persisted storage
        if (db.find(new BlockStateHelper().headerHashKey(BigInteger.ZERO)).isPresent()) {
            BlockState.getInstance().initialize(db);//Initialize BlockState from already existing data
        } else {
            GenesisBlockHash genesisBlockHash = AppBean.getBean(GenesisBlockHash.class);
            BlockState.getInstance().initialize(db,
                    genesisBlockHash.getGenesisBlockHeader()); //Initialize BlockState from genesis block

            TrieStructure<NodeData> trie = genesisBlockHash.getGenesisTrie();
            trieStorage.insertTrieStorage(trie);
        }
    }
}
