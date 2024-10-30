package com.limechain.babe.state;

import com.limechain.babe.Authorship;
import com.limechain.chain.lightsyncstate.BabeEpoch;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.javatuples.Pair;

import java.math.BigInteger;

/**
 * Represents the BABE constant and secondary slot.
 */
@Getter
@AllArgsConstructor
public class EpochDescriptor {
    private Pair<BigInteger, BigInteger> constant;
    private BabeEpoch.BabeAllowedSlots allowedSlots;
    //TODO: this may be relocated to different place
    @Setter
    private BigInteger threshold;

    public EpochDescriptor(Pair<BigInteger, BigInteger> constant, BabeEpoch.BabeAllowedSlots allowedSlots) {
        this.constant = constant;
        this.allowedSlots = allowedSlots;
    }

    public static EpochDescriptor build(Pair<BigInteger, BigInteger> constant,
                                        BabeEpoch.BabeAllowedSlots allowedSlots,
                                        EpochData epochData) {
        var result = new EpochDescriptor(constant, allowedSlots);
        result.setThreshold(
                Authorship.calculatePrimaryThreshold(
                        constant,
                        epochData.getAuthorities(),
                        epochData.getAuthorityIndex()
                )
        );
        return result;
    }
}
