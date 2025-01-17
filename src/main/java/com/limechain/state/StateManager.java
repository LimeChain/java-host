package com.limechain.state;

import com.limechain.babe.state.EpochState;
import com.limechain.grandpa.state.RoundState;
import com.limechain.storage.block.state.BlockState;
import com.limechain.sync.state.SyncState;
import com.limechain.transaction.TransactionState;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Getter
public class StateManager {
    private final SyncState syncState;
    private final RoundState roundState;
    private final EpochState epochState;
    private final TransactionState transactionState;
    private final BlockState blockState;
}
