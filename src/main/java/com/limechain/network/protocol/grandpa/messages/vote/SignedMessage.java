package com.limechain.network.protocol.grandpa.messages.vote;

import com.limechain.grandpa.vote.SubRound;
import io.emeraldpay.polkaj.types.Hash256;
import io.emeraldpay.polkaj.types.Hash512;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SignedMessage {
    private SubRound stage;
    private Hash256 blockHash;
    private BigInteger blockNumber;
    private Hash512 signature;
    private Hash256 authorityPublicKey;
}
