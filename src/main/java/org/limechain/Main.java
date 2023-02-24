package org.limechain;

import org.limechain.chain.ChainService;
import org.limechain.config.AppConfig;
import org.limechain.lightClient.LightClient;

public class Main {
    public static void main(String[] args) {
        AppConfig appConfig = new AppConfig(args);
        ChainService chainService = new ChainService(appConfig);
        LightClient client = new LightClient(chainService);

        client.start();
    }
}