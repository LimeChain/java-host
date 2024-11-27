package com.limechain.network.protocol.blockannounce.messages;

import com.limechain.network.protocol.warp.dto.BlockHeader;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlockAnnounceMessage {

    private BlockHeader header;
    private boolean bestBlock;
}
