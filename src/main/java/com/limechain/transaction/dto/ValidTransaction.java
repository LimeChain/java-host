package com.limechain.transaction.dto;

import io.libp2p.core.PeerId;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

@Value
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ValidTransaction implements Comparable<ValidTransaction> {

    @EqualsAndHashCode.Include
    Extrinsic extrinsic;

    @Nullable
    TransactionValidity transactionValidity;

    HashSet<PeerId> ignore = new HashSet<>();

    public int compareTo(@NotNull ValidTransaction transaction) {
        return transaction.transactionValidity.getPriority()
                .compareTo(this.transactionValidity.getPriority());
    }
}
