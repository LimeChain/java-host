package com.limechain.babe.api;

import com.limechain.network.protocol.warp.dto.BlockHeader;
import lombok.Data;

import java.math.BigInteger;

@Data
public class BlockEquivocationProof {
    byte[] publicKey;
    BigInteger slotNumber;
    BlockHeader firstBlockHeader;
    BlockHeader secondBlockHeader;
}