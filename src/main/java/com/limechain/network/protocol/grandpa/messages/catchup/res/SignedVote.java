package com.limechain.network.protocol.grandpa.messages.catchup.res;

import com.limechain.network.protocol.grandpa.messages.commit.Vote;
import io.emeraldpay.polkaj.types.Hash256;
import io.emeraldpay.polkaj.types.Hash512;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SignedVote implements Serializable {
    private Vote vote;
    private Hash512 signature;
    private Hash256 authorityPublicKey;
}
