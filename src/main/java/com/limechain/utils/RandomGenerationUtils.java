package com.limechain.utils;

import com.limechain.network.Network;
import io.ipfs.multiaddr.MultiAddress;

import java.util.Random;

public class RandomGenerationUtils {
    public static byte[] generateBytes(int length) {
        Random generator = new Random(0);
        byte[] bytes = new byte[length];
        generator.nextBytes(bytes);
        return bytes;
    }

    private static int generateRandomPort(){
        return 10000 + new Random().nextInt(50000);
    }

    public static MultiAddress generateRandomAddress(){
        return new MultiAddress(Network.LOCAL_IPV4_TCP_ADDRESS + generateRandomPort());
    }
}
