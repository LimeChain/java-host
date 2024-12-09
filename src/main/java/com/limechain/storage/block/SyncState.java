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
import org.javatuples.Pair;

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
    @Setter
    private Authority[] authoritySet;
    private BigInteger latestRound;
    private Hash256 stateRoot;
    private BigInteger setId;

    public SyncState(GenesisBlockHash genesisBlockHashCalculator, KVRepository<String, Object> repository) {
        this.genesisBlockHashCalculator = genesisBlockHashCalculator;
        this.genesisBlockHash = genesisBlockHashCalculator.getGenesisHash();
        this.repository = repository;

        loadPersistedState();
        this.startingBlock = this.lastFinalizedBlockNumber;
    }

    private void loadPersistedState() {
        this.lastFinalizedBlockNumber =
                (BigInteger) repository.find(DBConstants.LAST_FINALIZED_BLOCK_NUMBER).orElse(BigInteger.ZERO);
        this.lastFinalizedBlockHash = new Hash256(
                (byte[]) repository.find(DBConstants.LAST_FINALIZED_BLOCK_HASH).orElse(genesisBlockHash.getBytes()));
        this.authoritySet = (Authority[]) repository.find(DBConstants.AUTHORITY_SET).orElse(new Authority[0]);
        this.latestRound = (BigInteger) repository.find(DBConstants.LATEST_ROUND).orElse(BigInteger.ONE);
        byte[] stateRootBytes = (byte[]) repository.find(DBConstants.STATE_ROOT).orElse(null);
        this.stateRoot = stateRootBytes != null ? new Hash256(stateRootBytes) : genesisBlockHashCalculator
                .getGenesisBlockHeader().getStateRoot();
        this.setId = (BigInteger) repository.find(DBConstants.SET_ID).orElse(BigInteger.ZERO);
    }

    public void persistState() {
        repository.save(DBConstants.LAST_FINALIZED_BLOCK_NUMBER, lastFinalizedBlockNumber);
        repository.save(DBConstants.LAST_FINALIZED_BLOCK_HASH, lastFinalizedBlockHash.getBytes());
        repository.save(DBConstants.AUTHORITY_SET, authoritySet);
        repository.save(DBConstants.LATEST_ROUND, latestRound);
        repository.save(DBConstants.STATE_ROOT, stateRoot.getBytes());
        repository.save(DBConstants.SET_ID, setId);
    }

    public void finalizeHeader(BlockHeader header) {
        this.lastFinalizedBlockNumber = header.getBlockNumber();
        this.lastFinalizedBlockHash = header.getHash();
        this.stateRoot = header.getStateRoot();
    }

    public Pair<Hash256, Hash256> getPair() {
        return new Pair<>(lastFinalizedBlockHash, stateRoot);
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

    public BigInteger incrementSetId() {
        this.setId = this.setId.add(BigInteger.ONE);
        return setId;
    }

    public void resetRound() {
        this.latestRound = BigInteger.ONE;
    }

    public void setLightSyncState(LightSyncState initState) {
        this.setId = initState.getGrandpaAuthoritySet().getSetId();
        setAuthoritySet(initState.getGrandpaAuthoritySet().getCurrentAuthorities());
        finalizeHeader(initState.getFinalizedBlockHeader());
    }

}
