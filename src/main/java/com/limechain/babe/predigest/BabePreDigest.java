package com.limechain.babe.predigest;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigInteger;

@Data
@AllArgsConstructor
public class BabePreDigest {
     private PreDigestType type;
     private int authorityIndex;
     private BigInteger slotNumber;
     private byte[] vrfOutput;
     private byte[] vrfProof;
}
