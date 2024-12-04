package com.limechain.babe.api.scale;

import com.limechain.babe.api.BlockEquivocationProof;
import com.limechain.network.protocol.blockannounce.scale.BlockHeaderScaleWriter;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import io.emeraldpay.polkaj.scale.ScaleWriter;
import io.emeraldpay.polkaj.scale.writer.UInt64Writer;

import java.io.IOException;

public class BlockEquivocationProofWriter implements ScaleWriter<BlockEquivocationProof> {

    private final UInt64Writer uint64Writer;

    public BlockEquivocationProofWriter() {
        this.uint64Writer = new UInt64Writer();
    }

    @Override
    public void write(ScaleCodecWriter writer, BlockEquivocationProof blockEquivocationProof) throws IOException {
        writer.writeByteArray(blockEquivocationProof.getPublicKey());
        uint64Writer.write(writer, blockEquivocationProof.getSlotNumber());
        writer.write(BlockHeaderScaleWriter.getInstance(), blockEquivocationProof.getFirstBlockHeader());
        writer.write(BlockHeaderScaleWriter.getInstance(), blockEquivocationProof.getSecondBlockHeader());
    }
}
