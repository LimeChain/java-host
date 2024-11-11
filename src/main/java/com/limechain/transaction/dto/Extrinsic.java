package com.limechain.transaction.dto;

import lombok.Value;
import org.apache.tomcat.util.buf.HexUtils;

@Value
public class Extrinsic {

    byte[] data;

    @Override
    public String toString() {
        return HexUtils.toHexString(data);
    }
}
