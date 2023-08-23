package com.limechain.sync.warpsync;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.constants.GenesisBlockHash;
import com.limechain.network.Network;
import com.limechain.network.protocol.blockannounce.NodeRole;
import com.limechain.network.protocol.blockannounce.scale.BlockAnnounceHandshake;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import com.limechain.network.protocol.grandpa.messages.neighbour.NeighbourMessage;
import com.limechain.rpc.server.AppBean;
import com.limechain.runtime.Runtime;
import com.limechain.storage.DBConstants;
import com.limechain.storage.KVRepository;
import com.limechain.sync.JustificationVerifier;
import com.limechain.sync.warpsync.dto.StateDto;
import com.limechain.trie.Trie;
import io.emeraldpay.polkaj.types.Hash256;
import io.libp2p.core.PeerId;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import org.javatuples.Pair;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

@Getter
@Setter
@Log
public class SyncedState {
    public static final int NEIGHBOUR_MESSAGE_VERSION = 1;
    private static final SyncedState INSTANCE = new SyncedState();
    private Hash256 lastFinalizedBlockHash;
    private boolean isFinished;
    private Hash256 stateRoot;
    private BigInteger lastFinalizedBlockNumber = BigInteger.ZERO;
    private Authority[] authoritySet;
    private BigInteger setId;

    private BigInteger latestSetId = BigInteger.ZERO;

    private BigInteger latestRound = BigInteger.ONE;

    private byte[] runtimeCode;
    private byte[] heapPages;
    private Runtime runtime;
    private boolean warpSyncFinished;
    private KVRepository<String, Object> repository;
    private Trie trie;

    public static SyncedState getInstance() {
        return INSTANCE;
    }

    public BlockAnnounceHandshake getHandshake() {
        Hash256 genesisBlockHash;
        Network network = AppBean.getBean(Network.class);
        switch (network.getChain()) {
            case POLKADOT -> genesisBlockHash = GenesisBlockHash.POLKADOT;
            case KUSAMA -> genesisBlockHash = GenesisBlockHash.KUSAMA;
            case WESTEND -> genesisBlockHash = GenesisBlockHash.WESTEND;
            case LOCAL -> genesisBlockHash = GenesisBlockHash.LOCAL;
            default -> throw new IllegalStateException("Unexpected value: " + network.chain);
        }

        Hash256 lastFinalizedBlockHash = this.getLastFinalizedBlockHash() == null
                ? genesisBlockHash
                : this.getLastFinalizedBlockHash();
        return new BlockAnnounceHandshake(
                NodeRole.LIGHT.getValue(),
                this.getLastFinalizedBlockNumber(),
                lastFinalizedBlockHash,
                genesisBlockHash
        );
    }

    public NeighbourMessage getNeighbourMessage() {
        return new NeighbourMessage(
                NEIGHBOUR_MESSAGE_VERSION,
                this.latestRound,
                this.setId,
                this.lastFinalizedBlockNumber
        );
    }

    public void syncCommit(CommitMessage commitMessage, PeerId peerId) {
        boolean verified = JustificationVerifier.verify(commitMessage.getPrecommits(), commitMessage.getRoundNumber());
        if (!verified) {
            log.log(Level.WARNING, "Could not verify commit from peer: " + peerId);
            return;
        }

        if (warpSyncFinished) {
            updateState(commitMessage);
        }
    }

    private synchronized void updateState(CommitMessage commitMessage) {
        if (commitMessage.getVote().getBlockNumber().compareTo(lastFinalizedBlockNumber) < 1) {
            return;
        }
        latestRound = commitMessage.getRoundNumber();
        lastFinalizedBlockHash = commitMessage.getVote().getBlockHash();
        lastFinalizedBlockNumber = commitMessage.getVote().getBlockNumber();
        log.log(Level.INFO, "Reached block #" + lastFinalizedBlockNumber);
        persistState();
    }

    public void persistState() {
        List<Pair<String, BigInteger>> authorities = Arrays
                .stream(authoritySet)
                .map(authority -> {
                    String key = authority.getPublicKey().toString();
                    BigInteger weight = authority.getWeight();
                    return new Pair<>(key, weight);
                }).toList();

        StateDto stateDto = new StateDto(
                latestRound,
                lastFinalizedBlockHash.toString(),
                lastFinalizedBlockNumber,
                authorities,
                setId
        );
        repository.save(DBConstants.SYNC_STATE_KEY, stateDto);
    }

    public boolean loadState() {
        Optional<Object> syncState = repository.find(DBConstants.SYNC_STATE_KEY);
        if (syncState.isPresent() && syncState.get() instanceof StateDto state) {
            this.latestRound = state.latestRound();
            this.lastFinalizedBlockHash = Hash256.from(state.lastFinalizedBlockHash());
            this.lastFinalizedBlockNumber = state.lastFinalizedBlockNumber();
            this.authoritySet = state.authoritySet()
                    .stream()
                    .map(pair -> {
                        Hash256 publicKey = Hash256.from(pair.getValue0());
                        BigInteger weight = pair.getValue1();
                        return new Authority(publicKey, weight);
                    }).toArray(Authority[]::new);

            this.setId = state.setId();

            return true;
        }
        return false;
    }

    public void saveProofState(byte[][] proof) {
        repository.save(DBConstants.STATE_TRIE_MERKLE_PROOF, proof);
        repository.save(DBConstants.STATE_TRIE_ROOT_HASH, stateRoot.toString());
    }

    public byte[][] loadProof() {
        return (byte[][]) repository.find(DBConstants.STATE_TRIE_MERKLE_PROOF).orElse(null);
    }

    public Hash256 loadStateRoot() {
        Object storedRootState = repository.find(DBConstants.STATE_TRIE_ROOT_HASH).orElse(null);
        return storedRootState == null ? null : Hash256.from(storedRootState.toString());
    }
}