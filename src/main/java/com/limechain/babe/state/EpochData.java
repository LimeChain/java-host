package com.limechain.babe.state;

import com.limechain.chain.lightsyncstate.Authority;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Represents the supplied authority set and randomness.
 */
@Getter
@AllArgsConstructor
public class EpochData {
    private List<Authority> authorities;
    private byte[] randomness;
    //TODO: this may be relocated to different place
    private int authorityIndex;

    public EpochData(List<Authority> authorities, byte[] randomness) {
        this.authorities = authorities;
        this.randomness = randomness;
    }
}
