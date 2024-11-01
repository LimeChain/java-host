package com.limechain.babe.state;

import com.limechain.babe.api.BabeApiConfiguration;
import com.limechain.babe.consesus.scale.BabeConsensusMessageReader;
import com.limechain.babe.consesus.BabeConsensusMessage;
import com.limechain.runtime.RuntimeEndpoint;
import com.limechain.storage.block.BlockState;
import com.limechain.utils.BigIntegerUtils;
import com.limechain.utils.scale.ScaleUtils;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.Instant;

/**
 * Represents the state information for an epoch in the system.
 * This class encapsulates all the necessary configuration and parameters related to a specific epoch.
 */
@Getter
@Component
public class EpochState {
    private BigInteger slotDuration;
    private BigInteger epochLength;
    private EpochData currentEpochData;
    private EpochDescriptor currentEpochDescriptor;
    private EpochData nextEpochData;
    private long disabledAuthority;
    private EpochDescriptor nextEpochDescriptor;
    private BigInteger epochIndex;
    private BigInteger epochStartSlotNumber;
    private BigInteger epochEndSlotNumber;

    public void initialize(BabeApiConfiguration babeApiConfiguration) {
        this.slotDuration = babeApiConfiguration.getSlotDuration();
        this.epochLength = babeApiConfiguration.getEpochLength();
        this.currentEpochData = new EpochData(babeApiConfiguration.getAuthorities(), babeApiConfiguration.getRandomness());
        this.currentEpochDescriptor = new EpochDescriptor(babeApiConfiguration.getConstant(), babeApiConfiguration.getAllowedSlots());
    }

    public void updateNextEpochBlockConfig(byte[] message) {
        BabeConsensusMessage babeConsensusMessage = ScaleUtils.Decode.decode(message, new BabeConsensusMessageReader());
        switch (babeConsensusMessage.getFormat()) {
            case NEXT_EPOCH_DATA -> this.nextEpochData = babeConsensusMessage.getNextEpochData();
            case DISABLED_AUTHORITY -> this.disabledAuthority = babeConsensusMessage.getDisabledAuthority();
            case NEXT_EPOCH_DESCRIPTOR -> this.nextEpochDescriptor = babeConsensusMessage.getNextEpochDescriptor();
        }

        setEpochIndex();
    }

    public BigInteger getCurrentSlotNumber() {
        return BigInteger.valueOf(Instant.now().toEpochMilli()).divide(slotDuration);
    }

    //TODO: We need to get first block PreDigest in order to get the genesis_slot
    private void setEpochIndex() {
        this.epochIndex = BigIntegerUtils.divideAndRoundUp(BigInteger.valueOf(1234), epochLength);
        this.setEpochStartAndEndSlotNumber();
    }

    private void setEpochStartAndEndSlotNumber() {
        byte[] bytes = BlockState.getInstance().callRuntime(RuntimeEndpoint.BABE_API_CURRENT_EPOCH_START, null);

        this.epochStartSlotNumber = BigInteger.ONE;
        this.epochEndSlotNumber = epochStartSlotNumber.add(epochLength);
    }

    //TODO: Implement
//
//        pub fn epoch_start_slot(epoch_index: u64, genesis_slot: Slot, epoch_duration: u64) -> Slot {
//        // (epoch_index * epoch_duration) + genesis_slot
//
//	const PROOF: &str = "slot number is u64; it should relate in some way to wall clock time; \
//						 if u64 is not enough we should crash for safety; qed.";
//
//        epoch_index
//                .checked_mul(epoch_duration)
//                .and_then(|slot| slot.checked_add(*genesis_slot))
//		.expect(PROOF)
//                .into()
//    }
    public void setEpochStartSlotNumber() {
    }
}
