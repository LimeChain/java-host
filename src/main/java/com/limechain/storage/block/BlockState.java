package com.limechain.storage.block;

import com.limechain.exception.global.MissingObjectException;
import com.limechain.exception.storage.BlockNodeNotFoundException;
import com.limechain.exception.storage.BlockNotFoundException;
import com.limechain.exception.storage.BlockStorageGenericException;
import com.limechain.exception.storage.HeaderNotFoundException;
import com.limechain.exception.storage.LowerThanRootException;
import com.limechain.exception.storage.RoundAndSetIdNotFoundException;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockBody;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.scale.reader.BlockBodyReader;
import com.limechain.network.protocol.warp.scale.writer.BlockBodyWriter;
import com.limechain.rpc.subscriptions.chainsub.ChainSub;
import com.limechain.runtime.Runtime;
import com.limechain.storage.DBConstants;
import com.limechain.storage.KVRepository;
import com.limechain.storage.block.tree.BlockNode;
import com.limechain.storage.block.tree.BlockTree;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
import org.javatuples.Pair;
import org.springframework.util.SerializationUtils;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Contains the historical block data of the blockchain, including block headers and bodies.
 * It wraps the blocktree (which contains unfinalized blocks) and the database (which contains finalized blocks).
 */
@Log
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class BlockState {
    @Getter
    private static final BlockState instance = new BlockState();
    private final BlockStateHelper helper = new BlockStateHelper();
    private final Map<Hash256, Block> unfinalizedBlocks = new HashMap<>();
    private BlockTree blockTree;
    private KVRepository<String, Object> db;
    @Getter
    private Hash256 genesisHash;
    @Getter
    private Hash256 lastFinalized;
    @Getter
    private boolean initialized;
    @Getter
    private final BlockingQueue<Pair<Instant, BlockHeader>> pendingBlocksQueue = new LinkedBlockingQueue<>();

    /**
     * Initializes the BlockState instance from genesis
     *
     * @param repository the kvrepository used to store the block state
     * @param header     the genesis block header
     */
    public void initialize(final KVRepository<String, Object> repository, final BlockHeader header) {
        if (initialized) {
            throw new IllegalStateException("BlockState already initialized");
        }
        initialized = true;

        this.blockTree = new BlockTree(header);
        this.db = repository;

        final Hash256 headerHash = header.getHash();
        this.genesisHash = headerHash;
        this.lastFinalized = headerHash;

        setArrivalTime(headerHash, Instant.now());
        setHeader(header);
        db.save(helper.headerHashKey(header.getBlockNumber()), headerHash.getBytes());
        setBlockBody(headerHash, new BlockBody(new ArrayList<>()));

        //set the latest finalized head to the genesis header
        setFinalizedHash(header, BigInteger.ZERO, BigInteger.ZERO);
    }

    /**
     * Initializes the BlockState instance from existing database
     *
     * @param repository the kvrepository used to store the block state
     */
    public void initialize(final KVRepository<String, Object> repository) {
        if (initialized) {
            throw new IllegalStateException("BlockState already initialized");
        }
        initialized = true;

        this.db = repository;

        this.genesisHash = getHashByNumberFromDb(BigInteger.ZERO);
        final BlockHeader lastHeader = getHighestFinalizedHeader();
        this.lastFinalized = lastHeader.getHash();
        this.blockTree = new BlockTree(lastHeader);
    }

    public void initializeAfterWarpSync(Hash256 lastFinalizedBlockHash, BigInteger lastFinalizedBlockNumber) {
        BlockNode parentBlock = new BlockNode(
                lastFinalizedBlockHash,
                null,
                lastFinalizedBlockNumber.longValue()
        );

        this.blockTree = new BlockTree(parentBlock);
        this.lastFinalized = lastFinalizedBlockHash;
    }

    /**
     * Check if the hash is part of the unfinalized blocks in-memory or persisted in the database.
     *
     * @param hash the hash of the block header as byte array
     * @return true if the block header is found, false otherwise
     */
    public boolean hasHeader(final Hash256 hash) {
        if (unfinalizedBlocks.containsKey(hash)) {
            return true;
        }

        return hasHeaderInDatabase(hash);
    }

    /**
     * Check if the hash is persisted in the database.
     *
     * @param hash the hash of the block header as byte array
     * @return true if the block header is found, false otherwise
     */
    public boolean hasHeaderInDatabase(final Hash256 hash) {
        Optional<Object> foundHeader = db.find(helper.headerKey(hash));
        return foundHeader.isPresent();
    }

    /**
     * Get the block header for a given hash
     *
     * @param hash the hash of the block header as byte array
     * @return the block header
     */
    public BlockHeader getHeader(final Hash256 hash) {
        Block block = unfinalizedBlocks.get(hash);
        if (block != null && block.getHeader() != null) {
            return block.getHeader();
        }

        return loadHeaderFromDatabase(hash);
    }

    /**
     * Get the block hash on our best chain with the given number
     *
     * @param blockNum the block number
     * @return the block hash as byte array
     * @throws LowerThanRootException if the block number is lower than the root of the blocktree.
     * @throws BlockNotFoundException if the block is not found in the blocktree or the database.
     */
    public Hash256 getHashByNumber(final BigInteger blockNum) {
        try {
            // Try to get the hash from the block tree
            return blockTree.getHashByNumber(blockNum.longValue());
        } catch (LowerThanRootException lowerThanRootException) {
            throw lowerThanRootException;
        } catch (BlockStorageGenericException e) {
            // If error is LowerThanRootException, number has already been finalized, so check db
            return getHashByNumberFromDb(blockNum);
        }
    }

    /**
     * Get the block hash on our best chain with the given number from the database
     *
     * @param blockNum the block number
     * @return the block hash as byte array
     */
    private Hash256 getHashByNumberFromDb(BigInteger blockNum) {
        byte[] hash = (byte[]) db.find(helper.headerHashKey(blockNum)).orElse(null);

        if (hash == null) {
            throw new BlockNotFoundException("Block " + blockNum + " not found");
        }

        return new Hash256(hash);
    }

    /**
     * Get the block hash on our best chain with the given number
     *
     * @param blockNumber the block number
     * @return List of block hashes as byte array
     * @throws BlockNotFoundException if there is an issue in getting the block by number.
     */
    public List<Hash256> getHashesByNumber(final BigInteger blockNumber) {
        Block block;
        try {
            block = getBlockByNumber(blockNumber);
        } catch (Exception e) {
            throw new BlockNotFoundException("Getting block by number: " + e.getMessage(), e);
        }

        List<Hash256> blockHashes = blockTree.getAllBlocksAtNumber(block.getHeader().getParentHash());

        Hash256 hash = block.getHeader().getHash();
        if (!blockHashes.contains(hash)) {
            blockHashes.add(hash);
        }

        return blockHashes;
    }

    /**
     * Get all the descendants for a given block hash (including itself), by first checking in memory
     * and, if not found, reading from the block state database
     *
     * @param hash the hash of the block header as byte array
     * @return List of block hashes as byte array
     * @throws BlockNodeNotFoundException if the block node is not found in the block tree.
     */
    public List<Hash256> getAllDescendants(final Hash256 hash) {
        try {
            return blockTree.getAllDescendants(hash);
        } catch (BlockNodeNotFoundException blockNotFound) {
            throw blockNotFound;
        } catch (Exception ignored) {
            log.info("Failed to get all descendants from block tree, trying database");
        }

        final List<Hash256> allDescendants = new ArrayList<>();
        allDescendants.add(hash);
        final BlockHeader header = getHeader(hash);

        final List<Hash256> nextBlockHashes = getHashesByNumber(header.getBlockNumber().add(BigInteger.ONE));

        for (final Hash256 nextBlockHash : nextBlockHashes) {
            final BlockHeader nextHeader = getHeader(nextBlockHash);

            if (!Objects.equals(nextHeader.getParentHash(), hash)) {
                continue;
            }

            final List<Hash256> nextDescendants = getAllDescendants(nextBlockHash);
            allDescendants.addAll(nextDescendants);
        }

        return allDescendants;
    }

    /**
     * Get the block header on our best chain with the given number
     *
     * @param num the block number
     * @return the block header
     */
    public BlockHeader getHeaderByNumber(final BigInteger num) {
        final Hash256 hash = getHashByNumber(num);
        return getHeader(hash);
    }

    /**
     * Get the block on our best chain with the given number
     *
     * @param num the block number
     * @return the block
     */
    public Block getBlockByNumber(final BigInteger num) {
        final Hash256 hash = getHashByNumber(num);
        return getBlockByHash(hash);
    }

    /**
     * Get the block with the given hash
     *
     * @param hash the block hash
     * @return the block
     */
    public Block getBlockByHash(final Hash256 hash) {
        Block block = unfinalizedBlocks.get(hash);
        if (block != null) {
            return block;
        }

        BlockHeader header = getHeader(hash);

        BlockBody blockBody = getBlockBody(hash);
        return new Block(header, blockBody);
    }

    /**
     * Persist the block header in the database
     *
     * @param header the block header to be persisted
     * @return true if the block header was successfully persisted, false otherwise
     */
    public boolean setHeader(final BlockHeader header) {
        byte[] byteArray = helper.writeHeader(header);
        return db.save(helper.headerKey(header.getHash()), byteArray);
    }

    /**
     * Checks if the block body is in the database
     *
     * @param hash the block hash
     * @return true if the block body is in the database, false otherwise
     */
    public boolean hasBlockBody(final Hash256 hash) {
        if (unfinalizedBlocks.containsKey(hash)) {
            return true;
        }

        return db.find(helper.blockBodyKey(hash)).isPresent();
    }

    /**
     * Get the block body for a given hash
     *
     * @param hash the block hash
     * @return the block body
     * @throws BlockNotFoundException if the block body cannot be retrieved.
     */
    public BlockBody getBlockBody(final Hash256 hash) {
        Block block = unfinalizedBlocks.get(hash);
        if (block != null && block.getBody() != null) {
            return block.getBody();
        }

        byte[] data = (byte[]) db.find(helper.blockBodyKey(hash)).orElse(null);
        if (data == null) {
            throw new BlockNotFoundException("Failed to get block body from database");
        }

        return ScaleUtils.Decode.decode(data, BlockBodyReader.getInstance());
    }

    /**
     * Persist the block body in the database
     *
     * @param hash      the block hash
     * @param blockBody the block body to be persisted
     */
    public void setBlockBody(final Hash256 hash, final BlockBody blockBody) {
        byte[] encoded = ScaleUtils.Encode.encode(BlockBodyWriter.getInstance(), blockBody);
        db.save(helper.blockBodyKey(hash), encoded);
    }

    /**
     * Adds block to the blocktree and stores it in the database with the current time as arrival time
     *
     * @param block the block to be added
     */
    public void addBlock(final Block block) {
        addBlockWithArrivalTime(block, Instant.now());
    }

    /**
     * Adds block to the blocktree and stores it in the database with the given arrival time
     *
     * @param block       the block to be added
     * @param arrivalTime the arrival time of the block
     * @throws IllegalArgumentException if the block body is null.
     */
    public void addBlockWithArrivalTime(final Block block, final Instant arrivalTime) {
        if (block.getBody() == null) {
            throw new IllegalArgumentException("Block body cannot be null");
        }

        // Add block to blocktree
        blockTree.addBlock(block.getHeader(), arrivalTime);

        if (!unfinalizedBlocks.containsKey(block.getHeader().getHash())) {
            ChainSub.getInstance().notifyNewChainHead(block.getHeader());
        }
        // Store block in unfinalized blocks
        unfinalizedBlocks.put(block.getHeader().getHash(), block);
    }

    /**
     * Gets all the hashes of unfinalized blocks with the given number
     *
     * @param blockNum the block number
     * @return List of hashes of blocks
     * @throws HeaderNotFoundException if no header is found for the given block number.
     */
    public List<Hash256> getAllBlocksAtNumber(final BigInteger blockNum) {
        BlockHeader header = getHeaderByNumber(blockNum);

        if (header == null) {
            throw new HeaderNotFoundException("Header not found for block number: " + blockNum);
        }

        return getAllBlocksAtDepth(header.getParentHash());
    }

    /**
     * Gets all the hashes of unfinalized blocks with the depth of the given hash plus one
     *
     * @param hash the parent hash
     * @return List of hashes of blocks
     */
    public List<Hash256> getAllBlocksAtDepth(final Hash256 hash) {
        return blockTree.getAllBlocksAtNumber(hash);
    }

    /**
     * Verifies if a block is on our current chain by checking if it is a descendant of our best block
     *
     * @param header the block header
     * @return true if the block is on our current chain, false otherwise
     * @throws HeaderNotFoundException if the best block header cannot be retrieved.
     */
    public boolean isBlockOnCurrentChain(final BlockHeader header) {
        BlockHeader bestBlock = bestBlockHeader();
        if (bestBlock == null) {
            throw new HeaderNotFoundException("Best block header cannot be retrieved");
        }

        // If the new block's number is greater than our best block's number, then it is on our current chain.
        if (header.getBlockNumber().compareTo(bestBlock.getBlockNumber()) > 0) {
            return true;
        }

        return isDescendantOf(header.getHash(), bestBlock.getHash());
    }

    /**
     * Gets the current non finalized best block's hash
     *
     * @return the best block's hash
     */
    public Hash256 bestBlockHash() {
        return blockTree.bestBlockHash();
    }

    /**
     * Gets the current non finalized best block's header
     *
     * @return the best block's header
     */
    public BlockHeader bestBlockHeader() {
        Hash256 bestHash = bestBlockHash();
        return getHeader(bestHash);
    }

    /**
     * Gets the current best block's state root hash
     *
     * @return the best block's state root hash as byte array
     */
    public Hash256 bestBlockStateRoot() {
        return bestBlockHeader().getStateRoot();
    }

    /**
     * Gets the given block's state root's hash
     *
     * @param blockHash the block hash
     * @return the given block's state root's hash as byte array
     */
    public Hash256 getBlockStateRoot(final Hash256 blockHash) {
        return getHeader(blockHash).getStateRoot();
    }

    /**
     * Gets the current non finalized best block's number
     *
     * @return the best block's number
     * @throws HeaderNotFoundException if the best block header cannot be retrieved.
     */
    public BigInteger bestBlockNumber() {
        BlockHeader header = bestBlockHeader();
        if (header == null) {
            throw new HeaderNotFoundException("Failed to get best block header");
        }
        return header.getBlockNumber();
    }

    /**
     * Gets the current non finalized best block
     *
     * @return the best block
     */
    public Block bestBlock() {
        return getBlockByHash(bestBlockHash());
    }

    /**
     * Gets the header of the block with the given hash from the database
     *
     * @param hash the block hash
     * @return the block header
     * @throws HeaderNotFoundException if the header is not found in the database.
     */
    public BlockHeader loadHeaderFromDatabase(final Hash256 hash) {
        Optional<Object> foundHeader = db.find(helper.headerKey(hash));

        if (foundHeader.isEmpty()) {
            throw new HeaderNotFoundException("Header not found in database");
        }

        return helper.readHeader((byte[]) foundHeader.get());
    }

    /**
     * Gets the sub-blockchain between the starting hash and the ending hash using both block tree and database
     *
     * @param startHash the hash of the block to start with
     * @param endHash   the hash of the block to end with
     * @return the list of block hashes
     */
    public List<Hash256> range(final Hash256 startHash, final Hash256 endHash) {
        final List<Hash256> hashes = new ArrayList<>();

        if (Objects.equals(startHash, endHash)) {
            hashes.add(startHash);
            return hashes;
        }

        final BlockHeader endHeader;
        try {
            endHeader = loadHeaderFromDatabase(endHash);
        } catch (HeaderNotFoundException e) {
            // end hash is not in the database, so we should lookup the
            // block that could be in memory and in the database as well
            return retrieveRange(startHash, endHash);
        }

        // end hash was found in the database, that means all the blocks
        // between start and end can be found in the database
        return retrieveRangeFromDatabase(startHash, endHeader);
    }

    /**
     * Gets the sub-blockchain between the starting hash and the ending hash using both block tree and database
     *
     * @param startHash the hash of the block to start with
     * @param endHash   the hash of the block to end with
     * @return the list of block hashes
     */
    public List<Hash256> retrieveRange(final Hash256 startHash, final Hash256 endHash) {
        List<Hash256> inMemoryHashes = blockTree.range(startHash, endHash);
        Hash256 firstItem = inMemoryHashes.get(0);

        // if the first item is equal to the startHash that means we got the range
        // from the in-memory blocktree
        if (Objects.equals(firstItem, startHash)) {
            return inMemoryHashes;
        }

        // since we got as many blocks as we could from the block tree but still missing blocks to
        // fulfil the range we should lookup in the database for the remaining ones, the first item in the hashes array
        // must be the block tree root that is also placed in the database
        // so we will start from its parent since it is already in the array
        BlockHeader blockTreeRootHeader = loadHeaderFromDatabase(firstItem);
        BlockHeader startingAtParentHeader = loadHeaderFromDatabase(blockTreeRootHeader.getParentHash());

        List<Hash256> inDatabaseHashes = retrieveRangeFromDatabase(startHash, startingAtParentHeader);

        List<Hash256> hashes = new ArrayList<>(inDatabaseHashes);
        hashes.addAll(inMemoryHashes);
        return hashes;
    }

    /**
     * Gets the sub-blockchain between the starting hash and the ending hash using only the database
     *
     * @param startHash the hash of the block to start with
     * @param endHeader the header of the block to end with
     * @return the list of block hashes
     * @throws IllegalArgumentException     if the start block number is greater than the end block number.
     * @throws BlockStorageGenericException if there is a start hash mismatch.
     */
    public List<Hash256> retrieveRangeFromDatabase(final Hash256 startHash, final BlockHeader endHeader) {
        BlockHeader startHeader = loadHeaderFromDatabase(startHash);
        if (startHeader.getBlockNumber().compareTo(endHeader.getBlockNumber()) > 0) {
            throw new IllegalArgumentException("Start block number is greater than end block number");
        }

        BigInteger blocksInRange = endHeader.getBlockNumber()
                .subtract(startHeader.getBlockNumber()).add(BigInteger.ONE);
        List<Hash256> hashes = new ArrayList<>(blocksInRange.intValue());

        int lastPosition = blocksInRange.intValue() - 1;

        hashes.add(0, startHash);
        hashes.add(lastPosition, endHeader.getHash());

        Hash256 inLoopHash = endHeader.getParentHash();
        for (int currentPosition = lastPosition - 1; currentPosition > 0; currentPosition--) {
            hashes.add(currentPosition, inLoopHash);

            BlockHeader inLoopHeader = loadHeaderFromDatabase(inLoopHash);
            inLoopHash = inLoopHeader.getParentHash();
        }

        // Verify that we ended up with the start hash
        if (!Objects.equals(inLoopHash, startHash)) {
            throw new BlockStorageGenericException("Start hash mismatch: expected " + startHash +
                    ", found: " + inLoopHash);
        }

        return hashes;
    }

    /**
     * Gets the sub-blockchain between the starting hash and the ending hash using only the block tree
     *
     * @param startHash the hash of the block to start with
     * @param endHash   the hash of the block to end with
     * @return the list of block hashes
     * @throws BlockStorageGenericException if the block tree is not initialized.
     */
    public List<Hash256> rangeInMemory(final Hash256 startHash, final Hash256 endHash) {
        if (blockTree == null) {
            throw new BlockStorageGenericException("Block tree is not initialized");
        }

        return blockTree.rangeInMemory(startHash, endHash);
    }

    /**
     * Checks if a given child block is a descendant of a given parent block
     *
     * @param ancestor   parent block to verify for
     * @param descendant child block to verify for
     * @return true if the child block is a descendant of the parent block, false otherwise
     * @throws BlockStorageGenericException if the block tree is not initialized.
     */
    public boolean isDescendantOf(final Hash256 ancestor, final Hash256 descendant) {
        if (blockTree == null) {
            throw new BlockStorageGenericException("Block tree is not initialized");
        }

        try {
            return blockTree.isDescendantOf(ancestor, descendant);
        } catch (BlockStorageGenericException e) {
            BlockHeader descendantHeader = getHeader(descendant);
            BlockHeader ancestorHeader = getHeader(ancestor);

            BlockHeader current = descendantHeader;
            while (current.getBlockNumber().compareTo(ancestorHeader.getBlockNumber()) > 0) {
                if (Objects.equals(current.getParentHash(), ancestor)) {
                    return true;
                }
                current = getHeader(current.getParentHash());
            }

            return false;
        }
    }

    /**
     * Gets the lowest common ancestor between two blocks in the tree
     *
     * @param startHash the hash of the first block
     * @param endHash   the hash of the second block
     * @return the lowest common ancestor
     */
    public Hash256 lowestCommonAncestor(final Hash256 startHash, final Hash256 endHash) {
        return blockTree.lowestCommonAncestor(startHash, endHash);
    }

    /**
     * Gets the leaves of the blocktree as an array of byte arrays
     *
     * @return the leaves of the blocktree as an array of byte arrays
     */
    public List<Hash256> leaves() {
        return blockTree.leaves();
    }

    /**
     * Sets the arrival time of a block
     *
     * @param hash the hash of the block
     * @param now  the arrival time of the block
     */
    public void setArrivalTime(final Hash256 hash, final Instant now) {
        db.save(helper.arrivalTimeKey(hash), now);
    }

    /**
     * Get the arrival time of a block
     *
     * @param hash the hash of the block
     * @return the arrival time of the block
     * @throws MissingObjectException if the arrival time is not found in the database.
     */
    public Instant getArrivalTime(final Hash256 hash) {
        Optional<Object> object = db.find(helper.arrivalTimeKey(hash));

        if (object.isEmpty()) {
            throw new MissingObjectException("Arrival time not found");
        }
        Object obj = object.get();

        if (obj instanceof Instant instant) {
            return instant;
        } else {
            return (Instant) SerializationUtils.deserialize((byte[]) obj);
        }
    }

    /**
     * Get the runtime for a given block hash
     *
     * @param blockHash the block hash
     * @return runtime for the block
     * @throws BlockStorageGenericException if the block node is not found in the block tree.
     */
    public Runtime getRuntime(final Hash256 blockHash) {
        try {
            return blockTree.getBlockRuntime(blockHash);
        } catch (BlockNodeNotFoundException e) {
            throw new BlockStorageGenericException("While getting runtime: ", e);
        }
    }

    /**
     * Get the runtime for the current best block.
     *
     * @return runtime for the best block.
     * @throws BlockStorageGenericException if the block node is not found in the block tree.
     */
    public Runtime getBestBlockRuntime() {
        try {
            return blockTree.getBlockRuntime(bestBlockHash());
        } catch (BlockNodeNotFoundException e) {
            throw new BlockStorageGenericException("Exception when retrieving best block runtime: ", e);
        }
    }

    /**
     * Store the runtime for a given block hash
     *
     * @param blockHash the block hash
     * @param runtime   the runtime to be stored
     */
    public void storeRuntime(final Hash256 blockHash, final Runtime runtime) {
        blockTree.storeRuntime(blockHash, runtime);
    }

    /**
     * Get all non-finalized blocks
     *
     * @return list of hashes of the non-finalized blocks
     */
    public List<Hash256> getNonfinalizedBlocks() {
        return blockTree.getAllBlocks();
    }

    /**
     * Get non-finalized block from hash
     *
     * @param hash the hash of the block
     * @return the block
     */
    public Block getUnfinalizedBlockFromHash(final Hash256 hash) {
        return unfinalizedBlocks.get(hash);
    }

    /* Block finalization */

    /**
     * Sets the hash of the latest finalized block
     *
     * @param header the header of the block
     * @param round  The round number of the finalized block.
     * @param setId  The set ID of the finalized block.
     * @throws BlockNodeNotFoundException if the block corresponding to the provided hash is not found.
     */
    public void setFinalizedHash(final BlockHeader header, final BigInteger round, final BigInteger setId) {
        Hash256 hash = header.getHash();
        if (!hasHeader(hash)) {
            throw new BlockNodeNotFoundException("Cannot finalise unknown block " + hash);
        }

        if (!hash.equals(genesisHash) && getHighestFinalizedNumber().compareTo(header.getBlockNumber()) >= 0) {
            throw new LowerThanRootException("Finalized block with number " + header.getBlockNumber() + " is lower than root");
        }

        handleFinalizedBlock(hash);
        db.save(helper.finalizedHashKey(round, setId), hash.getBytes());
        setHighestRoundAndSetID(round, setId);

        if (round.compareTo(BigInteger.ZERO) > 0) {
            //Notify that we have finalized a block
        }

        List<Hash256> pruned = blockTree.prune(hash);

        for (Hash256 prunedHash : pruned) {
            unfinalizedBlocks.remove(prunedHash);
            //Delete from trie the states of pruned blocks' state root
            //TODO: tries.delete(blockheader.StateRoot)
            //TODO: implement when the Trie is ready
        }

        // if nothing was previously finalized, set the first slot of the network to the
        // slot number of block 1, which is now being set as final
        if (Objects.equals(this.lastFinalized, this.genesisHash) && Objects.equals(hash, this.genesisHash)) {
            //TODO: Implement when BABE is implemented - setFirstSlotOnFinalisation
        }

        if (this.lastFinalized != hash) {
            //Delete from trie last finalized
            //TODO: implement when the Trie is ready
        }

        this.lastFinalized = hash;
    }

    /**
     * Gets the header of the latest finalized block
     */
    public BlockHeader getHighestFinalizedHeader() {
        Hash256 hash = getHighestFinalizedHash();

        return getHeader(hash);
    }

    /**
     * Gets the number of the latest finalized block
     */
    public BigInteger getHighestFinalizedNumber() {
        return getHighestFinalizedHeader().getBlockNumber();
    }

    /**
     * Gets the hash of the latest finalized block
     */
    public Hash256 getHighestFinalizedHash() {
        Pair<BigInteger, BigInteger> roundAndSet = getHighestRoundAndSetID();

        return getFinalizedHash(roundAndSet.getValue0(), roundAndSet.getValue1());
    }

    /**
     * Gets the hash of the finalized block for given round and setId
     */
    public Hash256 getFinalizedHash(final BigInteger round, final BigInteger setId) {
        Optional<Object> foundHash = db.find(helper.finalizedHashKey(round, setId));

        if (foundHash.isEmpty()) {
            throw new HeaderNotFoundException("Header not found in database");
        }

        return new Hash256((byte[]) foundHash.get());
    }

    /**
     * Stores the highest round and setId to the database
     *
     * @param round the number of the round
     * @param setId the current setId
     * @throws BlockNodeNotFoundException if the provided setId is less than the highest stored setId.
     */
    public void setHighestRoundAndSetID(final BigInteger round, final BigInteger setId) {
        try {
            final Pair<BigInteger, BigInteger> highestRoundAndSetID = getHighestRoundAndSetID();
            final BigInteger highestSetID = highestRoundAndSetID.getValue1();

            if (setId.compareTo(highestSetID) < 0) {
                throw new BlockStorageGenericException(
                        "SetID " + setId + " should be greater or equal to " + highestSetID);
            }
        } catch (RoundAndSetIdNotFoundException e) {
            // If there is no highest round and setId, then we can safely store the provided values
        }
        db.save(DBConstants.HIGHEST_ROUND_AND_SET_ID_KEY, helper.bigIntegersToByteArray(round, setId));

    }

    /**
     * Gets the highest saved round and setId from the database
     *
     * @return Pair of round and setId
     * @throws BlockNodeNotFoundException if there is a failure in retrieving the highest round and setID.
     */
    public Pair<BigInteger, BigInteger> getHighestRoundAndSetID() {
        Optional<Object> roundAndSetId = db.find(DBConstants.HIGHEST_ROUND_AND_SET_ID_KEY);
        byte[] data = (byte[]) roundAndSetId.orElse(null);

        if (data == null || data.length < 16) {
            throw new RoundAndSetIdNotFoundException("Failed to get highest round and setID");
        }

        return helper.bytesToRoundAndSetId(data);
    }

    /**
     * Store all the blocks between last saved finalized and current finalized block in database
     * and delete them from the unfinalized block map
     *
     * @param currentFinalizedHash the hash of the current finalized block
     * @throws BlockNotFoundException if a block in the unfinalized block map is not found.
     */
    public void handleFinalizedBlock(final Hash256 currentFinalizedHash) {
        if (Objects.equals(currentFinalizedHash, this.lastFinalized)) {
            return;
        }

        List<Hash256> subchain = rangeInMemory(lastFinalized, currentFinalizedHash);

        for (Hash256 subchainHash : subchain) {
            if (Objects.equals(subchainHash, genesisHash)) {
                continue;
            }

            Block block = unfinalizedBlocks.get(subchainHash);
            if (block == null) {
                throw new BlockNotFoundException("Failed to find block in unfinalized block map for hash " +
                        subchainHash);
            }

            setHeader(block.getHeader());
            setBlockBody(subchainHash, block.getBody());

            getRuntime(block.getHeader().getHash()).getTrieAccessor().persistChanges();

            Instant arrivalTime = blockTree.getArrivalTime(subchainHash);
            setArrivalTime(subchainHash, arrivalTime);

            db.save(helper.headerHashKey(block.getHeader().getBlockNumber()), subchainHash.getBytes());

            // Delete from the unfinalizedBlockMap and delete reference to in-memory trie
            unfinalizedBlocks.remove(subchainHash);
            ChainSub.getInstance().notifyFinalizedChainHead(block.getHeader());

            // Prune all the subchain hashes state trie from memory
            // but keep the state trie from the current finalized block
            //TODO: If currentFinalizedHash is not equal to subchain hash, delete subchain state trie
        }
    }

    public void addBlockToQueue(BlockHeader blockHeader) {
        Optional<Pair<Instant, BlockHeader>> optional = pendingBlocksQueue.stream()
                .filter(e -> e.getValue1().getHash().equals(blockHeader.getHash()))
                .findFirst();
        if (optional.isPresent()) {
            return;
        }

        pendingBlocksQueue.add(new Pair<>(Instant.now(), blockHeader));
    }
}
