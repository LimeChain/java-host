package com.limechain.babe.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.LinkedHashMap;

@Getter
@NoArgsConstructor
@ToString
public class InherentData {
    /**
     * The key are the bytes of an {@link InherentType} and the value are scale encoded bytes. Retains insertion order.
     */
    private final LinkedHashMap<byte[], byte[]> data = new LinkedHashMap<>();
}