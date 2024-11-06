package com.limechain.babe.state;

import com.limechain.babe.api.BabeApiConfiguration;
import com.limechain.babe.consensus.scale.BabeConsensusMessageReader;
import com.limechain.babe.consensus.BabeConsensusMessage;
import com.limechain.utils.scale.ScaleUtils;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.Instant;

/**
 * Represents the state information for the current/next epoch that is needed
 * for block production with BABE.
 * Note: Intended for use only when the host is configured as an Authoring Node.
 */
@Getter
@Component
public class EpochState {
    private BigInteger slotDuration;
    private BigInteger epochLength;
    private EpochData currentEpochData;
    private EpochDescriptor currentEpochDescriptor;
    private EpochData nextEpochData;
    private EpochDescriptor nextEpochDescriptor;
    private long disabledAuthority;
    private BigInteger genesisSlotNumber;

    public void initialize(BabeApiConfiguration babeApiConfiguration) {
        this.slotDuration = babeApiConfiguration.getSlotDuration();
        this.epochLength = babeApiConfiguration.getEpochLength();
        this.currentEpochData = new EpochData(babeApiConfiguration.getAuthorities(), babeApiConfiguration.getRandomness());
        this.currentEpochDescriptor = new EpochDescriptor(babeApiConfiguration.getConstant(), babeApiConfiguration.getAllowedSlots());
    }

    //TODO: This will be fixed in https://github.com/LimeChain/Fruzhin/issues/593
    public void updateNextEpochBlockConfig(byte[] message) {
        BabeConsensusMessage babeConsensusMessage = ScaleUtils.Decode.decode(message, new BabeConsensusMessageReader());
        switch (babeConsensusMessage.getFormat()) {
            case NEXT_EPOCH_DATA -> this.nextEpochData = babeConsensusMessage.getNextEpochData();
            case DISABLED_AUTHORITY -> this.disabledAuthority = babeConsensusMessage.getDisabledAuthority();
            case NEXT_EPOCH_DESCRIPTOR -> this.nextEpochDescriptor = babeConsensusMessage.getNextEpochDescriptor();
        }
    }

    public BigInteger getCurrentSlotNumber() {
        return BigInteger.valueOf(Instant.now().toEpochMilli()).divide(slotDuration);
    }

    public BigInteger getCurrentEpochStartSlotNumer() {
        return getCurrentEpochIndex().multiply(epochLength).add(genesisSlotNumber);
    }

    public BigInteger getCurrentEpochIndex() {
        return getCurrentSlotNumber().subtract(genesisSlotNumber).divide(epochLength);
    }
}
