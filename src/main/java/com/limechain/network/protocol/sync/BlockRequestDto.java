package com.limechain.network.protocol.sync;

import com.limechain.network.protocol.sync.pb.SyncMessage;
import io.emeraldpay.polkaj.types.Hash256;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BlockRequestDto {
    private Integer fields;
    private Hash256 hash;
    private Integer number;
    private SyncMessage.Direction direction;
    private int maxBlocks;
}
