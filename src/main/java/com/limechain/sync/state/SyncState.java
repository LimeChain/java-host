package com.limechain.sync.state;

import com.limechain.chain.lightsyncstate.LightSyncState;
import com.limechain.constants.GenesisBlockHash;
import com.limechain.exception.storage.HeaderNotFoundException;
import com.limechain.network.protocol.grandpa.messages.commit.CommitMessage;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.state.AbstractState;
import com.limechain.storage.DBConstants;
import com.limechain.storage.KVRepository;
import com.limechain.storage.block.state.BlockState;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.logging.Level;

@Log
@Getter
@Component
@RequiredArgsConstructor
public class SyncState extends AbstractState {

    private final GenesisBlockHash genesisBlockHashCalculator;
    private final KVRepository<String, Object> repository;
    private final BlockState blockState;

    private Hash256 lastFinalizedBlockHash;
    private Hash256 stateRoot;
    private BigInteger lastFinalizedBlockNumber;
    private BigInteger startingBlock;

    private Hash256 genesisBlockHash;

    @Override
    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("SyncState already initialized");
        }
        initialized = true;

        genesisBlockHash = genesisBlockHashCalculator.getGenesisHash();
        lastFinalizedBlockNumber = BigInteger.ZERO;
        lastFinalizedBlockHash = new Hash256(genesisBlockHash.getBytes());
        startingBlock = this.lastFinalizedBlockNumber;
        stateRoot = genesisBlockHashCalculator.getGenesisBlockHeader().getStateRoot();
    }

    @Override
    public void initializeFromDatabase() {
        if (initialized) {
            throw new IllegalStateException("SyncState already initialized");
        }
        initialized = true;

        loadFromDatabase();
    }

    private void loadFromDatabase() {
        this.lastFinalizedBlockNumber = repository.find(DBConstants.LAST_FINALIZED_BLOCK_NUMBER, BigInteger.ZERO);
        this.lastFinalizedBlockHash = new Hash256(
                repository.find(DBConstants.LAST_FINALIZED_BLOCK_HASH, genesisBlockHash.getBytes()));
        byte[] stateRootBytes = repository.find(DBConstants.STATE_ROOT, null);
        this.stateRoot = stateRootBytes != null ? new Hash256(stateRootBytes) : genesisBlockHashCalculator
                .getGenesisBlockHeader().getStateRoot();
    }

    @Override
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
            BlockHeader blockHeader = blockState.getHeader(commitMessage.getVote().getBlockHash());
            if (blockHeader != null) {
                if (!updateBlockState(commitMessage, blockHeader)) return;

                this.stateRoot = blockHeader.getStateRoot();
                this.lastFinalizedBlockHash = commitMessage.getVote().getBlockHash();
                this.lastFinalizedBlockNumber = commitMessage.getVote().getBlockNumber();

                log.log(Level.INFO, "Reached block #" + lastFinalizedBlockNumber);
            }
        } catch (HeaderNotFoundException ignored) {
            log.fine("Received commit message for a block that is not in the block store");
        }
    }

    private boolean updateBlockState(CommitMessage commitMessage, BlockHeader blockHeader) {
        try {
            blockState.setFinalizedHash(blockHeader, commitMessage.getRoundNumber(), commitMessage.getSetId());
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
