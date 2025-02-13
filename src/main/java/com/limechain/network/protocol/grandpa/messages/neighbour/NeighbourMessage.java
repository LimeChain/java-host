package com.limechain.network.protocol.grandpa.messages.neighbour;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NeighbourMessage {
    private int version;
    private BigInteger roundNumber;
    private BigInteger setId;
    private BigInteger lastFinalizedBlock;
}
