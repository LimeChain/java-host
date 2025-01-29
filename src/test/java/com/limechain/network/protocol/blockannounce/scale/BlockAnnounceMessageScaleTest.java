package com.limechain.network.protocol.blockannounce.scale;

import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.network.protocol.warp.dto.HeaderDigest;
import com.limechain.utils.StringUtils;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.types.Hash256;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockAnnounceMessageScaleTest {

    @Test
    void blockAnnounceMessageEncodeAndDecodeTest() {
        BlockHeader blockHeader = new BlockHeader();
        blockHeader.setBlockNumber(BigInteger.ONE);
        blockHeader.setParentHash(new Hash256(StringUtils.hexToBytes("0x4545454545454545454545454545454545454545454545454545454545454545")));
        blockHeader.setStateRoot(new Hash256(StringUtils.hexToBytes("0xb3266de137d20a5d0ff3a6401eb57127525fd9b2693701f0bf5a8a853fa3ebe0")));
        blockHeader.setExtrinsicsRoot(new Hash256(StringUtils.hexToBytes("0x03170a2e7597b7b7e3d84c05391d139a62b157e78786d8c082f29dcf4c111314")));
        blockHeader.setDigest(new HeaderDigest[]{});
        BlockAnnounceMessage blockAnnounceMessage = new BlockAnnounceMessage();
        blockAnnounceMessage.setHeader(blockHeader);
        blockAnnounceMessage.setBestBlock(true);

        byte[] encodedBlockAnnounceMessage = ScaleUtils.Encode.encode(
                BlockAnnounceMessageScaleWriter.getInstance(),
                blockAnnounceMessage
        );

        BlockAnnounceMessage decodedBlockAnnounceMessage = ScaleUtils.Decode.decode(encodedBlockAnnounceMessage, new BlockAnnounceMessageScaleReader());
        assertEquals(blockAnnounceMessage.isBestBlock(), decodedBlockAnnounceMessage.isBestBlock());

        BlockHeader decodedHeader = decodedBlockAnnounceMessage.getHeader();
        assertEquals(blockHeader.getBlockNumber(), decodedHeader.getBlockNumber());
        assertEquals(blockHeader.getParentHash(), decodedHeader.getParentHash());
        assertEquals(blockHeader.getStateRoot(), decodedHeader.getStateRoot());
        assertEquals(blockHeader.getExtrinsicsRoot(), decodedHeader.getExtrinsicsRoot());
        assertArrayEquals(blockHeader.getDigest(), decodedHeader.getDigest());
    }
}
