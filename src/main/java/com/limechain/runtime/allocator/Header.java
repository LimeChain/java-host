package com.limechain.runtime.allocator;

import com.limechain.runtime.memory.Memory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

/**
 * Allocation header preceding a memory block.
 * <br>
 * Can be one of two types:
 * <ul>
 *     <li>Free: containing a pointer to the next free block and signifying that the block after it is free.</li>
 *     <li>
 *         Occupied: containing the {@link Order order} of the block following it and signifying that it is occupied.
 *     </li>
 * </ul>
 * The header is written in memory as 64 bits: the most significant ones denoting its type
 * and the least significant - the header data (next free block or order).
 */
@Log
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Header {
    private static final long OCCUPIED_HEADER_MASK = 0x00000001_00000000L;
    private static final long FREE_HEADER_MASK = 0x00000000_00000000L;

    private final boolean occupied;

    private Integer nextFreeHeaderPointer;
    private Integer order;

    /**
     * Creates a header for an occupied block.
     *
     * @param order order of the occupied block
     * @return occupied header
     */
    public static Header occupied(int order) {
        Header header = new Header(true);
        log.finer( "Creating occupied header, order=" + order);
        header.order = order;
        return header;
    }

    /**
     * Creates a header for a free block.
     *
     * @param next pointer to the next free block of this order
     * @return free header
     */
    public static Header free(int next) {
        Header header = new Header(false);
        header.nextFreeHeaderPointer = next;
        log.finer("Creating free header, next=" + next);

        return header;
    }

    /**
     * Read a header from memory.
     *
     * @param pointer header address
     * @param memory  memory to read from
     * @return parsed header
     */
    public static Header fromMemory(int pointer, Memory memory) {
        log.finer("Reading header from memory, pointer=" + pointer);
        long rawHeader = memory.buffer().getLong(pointer);
        boolean occupied = (rawHeader & OCCUPIED_HEADER_MASK) != 0;
        int data = (int) rawHeader;

        return occupied ? Header.occupied(data) : Header.free(data);
    }

    /**
     * Returns the raw 64-bit representation of this header.
     *
     * @return 64-bit header
     */
    public long raw() {
        if (occupied) {
            return (long) order | OCCUPIED_HEADER_MASK;
        } else {
            return (long) nextFreeHeaderPointer | FREE_HEADER_MASK;
        }
    }
}
