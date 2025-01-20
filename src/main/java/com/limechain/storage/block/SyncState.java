package com.limechain.storage.block;

import com.limechain.chain.lightsyncstate.Authority;
import com.limechain.chain.lightsyncstate.LightSyncState;
import com.limechain.constants.GenesisBlockHash;
import com.limechain.exception.storage.HeaderNotFoundException;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.storage.DBConstants;
import com.limechain.storage.KVRepository;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.math.BigInteger;
import java.util.logging.Level;

@Getter
@Log
public class SyncState {

    private final GenesisBlockHash genesisBlockHashCalculator;
    private final KVRepository<String, Object> repository;
    private BigInteger lastFinalizedBlockNumber;
    private final BigInteger startingBlock;
    private final Hash256 genesisBlockHash;
    private Hash256 lastFinalizedBlockHash;
    private Hash256 stateRoot;

    public SyncState(GenesisBlockHash genesisBlockHashCalculator, KVRepository<String, Object> repository) {
        this.genesisBlockHashCalculator = genesisBlockHashCalculator;
        this.genesisBlockHash = genesisBlockHashCalculator.getGenesisHash();
        this.repository = repository;

        loadPersistedState();
        this.startingBlock = this.lastFinalizedBlockNumber;
    }

    private void loadPersistedState() {
        this.lastFinalizedBlockNumber = repository.find(DBConstants.LAST_FINALIZED_BLOCK_NUMBER, BigInteger.ZERO);
        this.lastFinalizedBlockHash = new Hash256(
                repository.find(DBConstants.LAST_FINALIZED_BLOCK_HASH, genesisBlockHash.getBytes()));
        byte[] stateRootBytes = repository.find(DBConstants.STATE_ROOT, null);
        this.stateRoot = stateRootBytes != null ? new Hash256(stateRootBytes) : genesisBlockHashCalculator
                .getGenesisBlockHeader().getStateRoot();
    }

    public void persistState() {
        repository.save(DBConstants.LAST_FINALIZED_BLOCK_NUMBER, lastFinalizedBlockNumber);
        repository.save(DBConstants.LAST_FINALIZED_BLOCK_HASH, lastFinalizedBlockHash.getBytes());
        repository.save(DBConstants.STATE_ROOT, stateRoot.getBytes());
    }

    public void finalizeHeader(BlockHeader header) {
        this.lastFinalizedBlockNumber = header.getBlockNumber();
        this.lastFinalizedBlockHash = header.getHash();
        this.stateRoot = header.getStateRoot();
    }

    public void finalizedCommitMessage(CommitMessage commitMessage) {
        try {
            Block blockByHash = BlockState.getInstance().getBlockByHash(commitMessage.getVote().getBlockHash());
            if (blockByHash != null) {
                if (!updateBlockState(commitMessage, blockByHash)) return;

                this.stateRoot = blockByHash.getHeader().getStateRoot();
                this.lastFinalizedBlockHash = commitMessage.getVote().getBlockHash();
                this.lastFinalizedBlockNumber = commitMessage.getVote().getBlockNumber();

                log.log(Level.INFO, "Reached block #" + lastFinalizedBlockNumber);
            }
        } catch (HeaderNotFoundException ignored) {
            log.fine("Received commit message for a block that is not in the block store");
        }
    }

    private static boolean updateBlockState(CommitMessage commitMessage, Block blockByHash) {
        try {
            BlockState.getInstance().setFinalizedHash(
                    blockByHash.getHeader(), commitMessage.getRoundNumber(), commitMessage.getSetId());
        } catch (Exception e) {
            log.fine(e.getMessage());
            return false;
        }
        return true;
    }

    public void setLightSyncState(LightSyncState initState) {
        finalizeHeader(initState.getFinalizedBlockHeader());
    }

}
