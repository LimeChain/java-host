package com.limechain.utils;

import com.limechain.network.NetworkService;
import io.ipfs.multiaddr.MultiAddress;
import lombok.experimental.UtilityClass;

import java.util.Random;

/**
 * A utility class that provides methods for generating random data.
 * The class is marked as final and has a private constructor to prevent instantiation.
 * All methods are static and can be called directly on the class.
 * The class currenly is used only in tests.
 */
@UtilityClass
public class RandomGenerationUtils {
    private static final Random RANDOM = new Random();

    /**
     * Generates an array of random bytes of a specified length.
     * This method uses a fixed seed to ensure the randomness is repeatable and predictable during tests or subsequent runs.
     *
     * @param length the length of the byte array to generate
     * @return a byte array filled with random bytes
     */
    public static byte[] generateBytes(int length) {
        Random generator = new Random(0);
        byte[] bytes = new byte[length];
        generator.nextBytes(bytes);
        return bytes;
    }

    /**
     * Generates a random port number within a specified range.
     * The method adds 10000 to a randomly generated number to ensure the port number is always between 10000 and 59999.
     *
     * @return a random port number
     */
    private static int generateRandomPort() {
        return 10000 + RANDOM.nextInt(50000);
    }

    /**
     * Generates a random IPFS MultiAddress using a standard local IP (IPv4) and a random TCP port.
     * This is useful for creating distinct network endpoints or addresses in a simulated network environment.
     *
     * @return a new MultiAddress instance representing a random local network address
     */
    public static MultiAddress generateRandomAddress() {
        return new MultiAddress(NetworkService.LOCAL_IPV4_TCP_ADDRESS + generateRandomPort());
    }
}
