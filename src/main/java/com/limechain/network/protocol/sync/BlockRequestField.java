package com.limechain.network.protocol.sync;

import lombok.Getter;

@Getter
public enum BlockRequestField {

    HEADER(0b0000_0001),
    BODY(0b0000_0010),
    JUSTIFICATION(0b0001_0000),
    ALL(HEADER.value | BODY.value | JUSTIFICATION.value);

    private final int value;

    BlockRequestField(int value) {
        this.value = value;
    }
}

