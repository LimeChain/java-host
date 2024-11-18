package com.limechain.babe;

import com.limechain.babe.coordinator.SlotChangeEvent;
import com.limechain.babe.coordinator.SlotChangeListener;
import com.limechain.babe.predigest.BabePreDigest;
import com.limechain.babe.state.EpochState;
import com.limechain.exception.scale.ScaleEncodingException;
import com.limechain.network.Network;
import com.limechain.network.protocol.blockannounce.messages.BlockAnnounceMessage;
import com.limechain.network.protocol.blockannounce.scale.BlockAnnounceMessageScaleWriter;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.rpc.server.AppBean;
import com.limechain.storage.block.BlockState;
import com.limechain.storage.crypto.KeyStore;
import io.emeraldpay.polkaj.scale.ScaleCodecWriter;
import org.apache.commons.collections4.map.HashedMap;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;

@Component
public class BabeService implements SlotChangeListener {

    private final BlockState blockState = BlockState.getInstance();
    private final EpochState epochState;
    private final KeyStore keyStore;
    private final Map<BigInteger, BabePreDigest> slotToPreRuntimeDigest = new HashedMap<>();
    private final Network network = AppBean.getBean(Network.class);

    public BabeService(EpochState epochState, KeyStore keyStore) {
        this.epochState = epochState;
        this.keyStore = keyStore;
    }

    private void executeEpochLottery(BigInteger epochIndex) {
        var epochStartSlotNumber = epochState.getEpochStartSlotNumber(epochIndex);
        var epochEndSlotNumber = epochStartSlotNumber.add(epochState.getEpochLength());

        for (BigInteger slot = epochStartSlotNumber; slot.compareTo(epochEndSlotNumber) < 0; slot = slot.add(BigInteger.ONE)) {
            BabePreDigest babePreDigest = Authorship.claimSlot(epochState, slot, keyStore);
            if (babePreDigest != null) {
                slotToPreRuntimeDigest.put(slot, babePreDigest);
            }
        }
    }

    @Override
    public void slotChanged(SlotChangeEvent event) {
        // TODO: Add implementation for building a block on every slot change
        if (event.isLastSlotFromCurrentEpoch()) {
            var nextEpochIndex = event.getEpochIndex().add(BigInteger.ONE);
            executeEpochLottery(nextEpochIndex);
        }
    }

    public byte[] createEncodedBlockAnnounceMessage(BlockHeader blockHeader) {
        BlockAnnounceMessage blockAnnounceMessage = new BlockAnnounceMessage();
        blockAnnounceMessage.setHeader(blockHeader);
        blockAnnounceMessage.setBestBlock(blockHeader.getHash().equals(blockState.bestBlockHash()));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ScaleCodecWriter writer = new ScaleCodecWriter(buf)) {
            writer.write(new BlockAnnounceMessageScaleWriter(), blockAnnounceMessage);
        } catch (IOException e) {
            throw new ScaleEncodingException(e);
        }
        return buf.toByteArray();
    }

    public void broadcastBlock(BlockHeader blockHeader) {
        network.sendBlockAnnounceMessage(createEncodedBlockAnnounceMessage(blockHeader));
    }
}
