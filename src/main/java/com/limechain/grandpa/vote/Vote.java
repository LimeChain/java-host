package com.limechain.grandpa.vote;

import com.limechain.network.protocol.warp.dto.BlockHeader;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Vote implements Serializable {

    private Hash256 blockHash;
    private BigInteger blockNumber;

    public static Vote fromBlockHeader(BlockHeader blockHeader) {
        Vote vote = new Vote();
        vote.setBlockHash(blockHeader.getHash());
        vote.setBlockNumber(blockHeader.getBlockNumber());

        return vote;
    }
}
