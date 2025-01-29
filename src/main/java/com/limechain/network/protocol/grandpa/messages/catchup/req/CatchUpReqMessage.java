package com.limechain.network.protocol.grandpa.messages.catchup.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CatchUpReqMessage {
    private BigInteger round;
    private BigInteger setId;
}
