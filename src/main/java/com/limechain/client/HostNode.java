package com.limechain.client;

import com.limechain.NodeService;
import com.limechain.ServiceState;
import com.limechain.constants.GenesisBlockHash;
import com.limechain.rpc.server.AppBean;
import com.limechain.storage.KVRepository;
import com.limechain.storage.block.state.BlockStateHelper;
import com.limechain.storage.trie.TrieStorage;
import com.limechain.trie.structure.TrieStructure;
import com.limechain.trie.structure.database.NodeData;

import java.math.BigInteger;
import java.util.List;

public abstract class HostNode {

    protected final List<NodeService> services;
    protected final List<ServiceState> states;

    public HostNode(List<NodeService> services, List<ServiceState> states) {
        this.services = services;
        this.states = states;
    }

    /**
     * Starts the client by assigning all dependencies and services from the spring boot application's context
     *
     * @apiNote the RpcApp is assumed to have been started
     * before constructing the clients in our current implementations,
     * as starting the clients relies on the application context
     */
    public void start() {
        initializeStates();
        services.forEach(NodeService::start);
    }

    protected void initializeStates() {
        // TODO: Is there a better way to decide whether we've got any database written?
        KVRepository<String, Object> db = AppBean.getBean(KVRepository.class); //presume this works

        if (db == null) {
            throw new IllegalStateException("Database is not initialized");
        }
        TrieStorage trieStorage = AppBean.getBean(TrieStorage.class);
        // if: database has some persisted storage
        if (db.find(BlockStateHelper.headerHashKey(BigInteger.ZERO)).isPresent()) {
            states.forEach(ServiceState::initializeFromDatabase);
        } else {
            GenesisBlockHash genesisBlockHash = AppBean.getBean(GenesisBlockHash.class);
            TrieStructure<NodeData> trie = genesisBlockHash.getGenesisTrie();
            trieStorage.insertTrieStorage(trie);

            states.forEach(ServiceState::initialize);
        }
    }
}
