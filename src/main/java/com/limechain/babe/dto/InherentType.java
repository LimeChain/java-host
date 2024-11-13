package com.limechain.babe.dto;

public enum InherentType {

    TIMESTAMP0("timstap0"),
    BABESLOT("babeslot"),
    // Not sure where this is used.
    UNCLES00("uncles00"),
    PARACHN0("parachn0"),
    // Not sure what this is used for.
    NEWHEADS("newheads");

    private final String value;

    InherentType(String value) {
        this.value = value;
    }

    public byte[] toByteArray() {
        byte[] bytes = new byte[8];
        byte[] stringBytes = value.getBytes();
        System.arraycopy(stringBytes, 0, bytes, 0, Math.min(stringBytes.length, 8));
        return bytes;
    }
}

