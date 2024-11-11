package com.limechain.sync.fullsync.inherents;

import org.javatuples.Pair;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Represents the totality of inherentes included in each block.
 *
 * @param timestamp Number of milliseconds since the UNIX epoch when the block is generated, ignoring leap seconds.
 *                  Its identifier passed to the runtime is: `timstap0`.
 * @see <a href="https://spec.polkadot.network/chap-state#tabl-inherent-data">the spec</a>
 */
public record InherentData(long timestamp) {

    /**
     * Turns this list of inherents into a list that can be passed as parameter to the runtime.
     */
    public List<Pair<byte[], byte[]>> asRawList() {
        return List.of(
                new Pair<>(
                        "timstap0".getBytes(StandardCharsets.US_ASCII),
                        longToBytesLittleEndian(timestamp)
                )
        );
    }

    private static byte[] longToBytesLittleEndian(long value) {
        byte[] bytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return bytes;
    }
}
