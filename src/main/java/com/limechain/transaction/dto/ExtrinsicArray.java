package com.limechain.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExtrinsicArray {

    private Extrinsic[] extrinsics;
}
