package com.limechain.babe.api;

import com.limechain.network.protocol.warp.dto.BlockHeader;
import lombok.Data;

import java.math.BigInteger;

@Data
public class BlockEquivocationProof {
    private byte[] publicKey;
    private BigInteger slotNumber;
    private BlockHeader firstBlockHeader;
    private BlockHeader secondBlockHeader;
}