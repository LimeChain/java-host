package com.limechain.babe.predigest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BabePreDigest {
     private PreDigestType type;
     private long authorityIndex;
     private BigInteger slotNumber;
     private byte[] vrfOutput;
     private byte[] vrfProof;
}
