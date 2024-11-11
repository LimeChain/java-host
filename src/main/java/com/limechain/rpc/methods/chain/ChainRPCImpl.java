package com.limechain.rpc.methods.chain;

import com.limechain.exception.storage.BlockNotFoundException;
import com.limechain.exception.storage.BlockStorageGenericException;
import com.limechain.exception.storage.HeaderNotFoundException;
import com.limechain.network.protocol.warp.dto.Block;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.storage.block.BlockState;
import com.limechain.utils.StringUtils;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Service
@AllArgsConstructor
public class ChainRPCImpl {

    private static final String HEX_PREFIX = "0x";
    private final BlockState blockState = BlockState.getInstance();

    /**
     * Converts a BlockHeader object into a map representation.
     * This map includes the block's digest, extrinsics root, number, parent hash, and state root.
     *
     * @param header The BlockHeader object to convert.
     * @return A map representation of the block header.
     */
    @NotNull
    public static Map<String, Object> headerToMap(BlockHeader header) {
        return Map.of(
                "digest", Map.of(
                        "logs", Arrays
                                .stream(header.getDigest())
                                .map(HeaderDigest::getMessage)
                                .map(StringUtils::toHexWithPrefix)
                                .toArray()
                ),
                "extrinsicsRoot", header.getExtrinsicsRoot().toString(),
                "number", HEX_PREFIX + header.getBlockNumber().toString(16),
                "parentHash", header.getParentHash().toString(),
                "stateRoot", header.getStateRoot().toString()
        );
    }

    /**
     * Retrieves the header of a specified block in the blockchain.
     * If no block hash is provided, it returns the header of the latest finalized block.
     *
     * @param blockHash the hex encoded hash of the block to retrieve the header for. If null, the latest block header is returned.
     * @return a map representing the block header, or null if the block cannot be found or the block state is not initialized.
     */
    public Map<String, Object> chainGetHeader(String blockHash) {
        if (!blockState.isInitialized()) {
            return null;
        }
        if (blockHash == null) {
            blockHash = blockState.getHighestFinalizedHash().toString();
        }

        final BlockHeader header;
        try {
            header = blockState.getHeader(Hash256.from(blockHash));
        } catch (HeaderNotFoundException e) {
            return null;
        }

        return headerToMap(header);
    }

    /**
     * Retrieves the full block data for a specified block in the blockchain.
     * If no block hash is provided, the latest finalized block is returned.
     *
     * @param blockHash The hex encoded hash of the block to retrieve. If null, the latest block is returned.
     * @return A map containing the block header and extrinsics, or null if the block cannot be found or the block state is not initialized.
     */
    public Map<String, Object> chainGetBlock(String blockHash) {
        if (!blockState.isInitialized()) {
            return null;
        }
        if (blockHash == null) {
            blockHash = blockState.getHighestFinalizedHash().toString();
        }

        Block block;
        try {
            block = blockState.getBlockByHash(Hash256.from(blockHash));
        } catch (HeaderNotFoundException | BlockNotFoundException e) {
            return null;
        }

        return Map.of(
                "block", Map.of("header", headerToMap(block.getHeader()),
                        "extrinsics", Arrays.stream(block.getBody().getExtrinsicsAsByteArray())
                                .map(StringUtils::toHexWithPrefix)
                                .toArray())
        );
    }

    /**
     * Retrieves the block hash for one or more block numbers.
     * If the block state is not initialized or no block numbers are provided, null is returned.
     *
     * @param blockNumbers An array of block numbers for which to retrieve the hashes.
     * @return A single block hash or a list of block hashes, or null if none can be found.
     */
    public Object chainGetBlockHash(Object[] blockNumbers) {
        if (!blockState.isInitialized()) {
            return null;
        }
        if (blockNumbers == null || blockNumbers.length == 0)
            return null;

        List<String> blockHashes = Arrays.stream(blockNumbers)
                .filter(Objects::nonNull)
                .map(parseObjectToBigInt())
                .filter(Objects::nonNull)
                .map(getBlockHashFromNum())
                .filter(Objects::nonNull)
                .map(Hash256::toString)
                .toList();

        if (blockHashes.isEmpty())
            return null;
        else if (blockHashes.size() == 1)
            return blockHashes.get(0);
        else
            return blockHashes;
    }

    /**
     * Converts an input object representing a block number into a BigInteger.
     * The input object can be of several types, indicating the 'n-th' block in the chain:
     * - HEX: A hex-encoded string prefixed with "0x" representing the block number.
     * - U32: An integer or long value directly representing the block number.
     * - ARRAY: An array of values, where each value can be either HEX or U32 type.
     * This method is designed to handle the flexible input types and convert them into a standardized BigInteger format.
     *
     * @return A function that takes an Object (HEX, U32, or ARRAY) and returns a BigInteger representation of the block number.
     * If the input is an array, the function processes each element according to its type (HEX or U32) and returns a list of BigInteger.
     * Returns null if the input cannot be converted to a BigInteger.
     */
    @NotNull
    private static Function<Object, BigInteger> parseObjectToBigInt() {
        return blockNumber -> {
            if (blockNumber instanceof String blockNumStr) {
                if (blockNumStr.startsWith(HEX_PREFIX)) {
                    return new BigInteger(blockNumStr.substring(2), 16);
                }
                return null;
            } else if (blockNumber instanceof Long blockNum) {
                return BigInteger.valueOf(blockNum);
            } else if (blockNumber instanceof Integer blockNum) {
                return BigInteger.valueOf(blockNum);
            } else
                return null;
        };
    }

    @NotNull
    private Function<BigInteger, Hash256> getBlockHashFromNum() {
        return blockNum -> {
            try {
                return blockState.getHashByNumber(blockNum);
            } catch (BlockStorageGenericException e) {
                return null;
            }
        };
    }

    /**
     * Retrieves the hash of the highest finalized block in the blockchain.
     * If the block state is not initialized, null is returned.
     *
     * @return The hex encoded hash of the highest finalized block, or null if the block state is not initialized.
     */
    public String chainGetFinalizedHead() {
        if (!blockState.isInitialized()) {
            return null;
        }
        return blockState.getHighestFinalizedHash().toString();
    }
}
