package com.limechain.state;

import com.limechain.babe.state.EpochState;
import com.limechain.grandpa.state.GrandpaSetState;
import com.limechain.storage.block.state.BlockState;
import com.limechain.sync.state.SyncState;
import com.limechain.transaction.TransactionState;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Getter
@Component
@AllArgsConstructor
public class StateManager {

    private final SyncState syncState;
    private final GrandpaSetState grandpaSetState;
    private final EpochState epochState;
    private final TransactionState transactionState;
    private final BlockState blockState;
}
