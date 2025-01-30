package com.limechain.rpc.server;

import com.limechain.cli.Cli;
import com.limechain.config.SystemInfo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Main RPC Spring application class.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.limechain.babe",
        "com.limechain.grandpa",
        "com.limechain.network",
        "com.limechain.rpc.config",
        "com.limechain.rpc.methods",
        "com.limechain.rpc.server",
        "com.limechain.runtime",
        "com.limechain.storage",
        "com.limechain.sync.state",
        "com.limechain.transaction"
})
public class RpcApp {

    /**
     * Port the Spring app will run on
     */
    private static final String SERVER_PORT = "9922";
    private static final String SERVER_LOCAL_ADDR = "127.0.0.1";

    /**
     * The reference to the underlying SpringApplication
     */
    private final SpringApplication app;

    public RpcApp() {
        this.app = new SpringApplication(RpcApp.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", SERVER_PORT));
    }

    /**
     * Spring application context
     */
    private ConfigurableApplicationContext springCtx;

    /**
     * Starts the Spring application.
     *
     * @param cliArgs arguments that will be passed as
     *                ApplicationArguments to {@link com.limechain.rpc.config.CommonConfig}.
     * @see com.limechain.rpc.config.CommonConfig#hostConfig(com.limechain.cli.CliArguments)
     */
    public void start(String[] cliArgs) {
        if (Arrays.stream(cliArgs).noneMatch(arg -> arg.endsWith(Cli.PUBLIC_RPC))) {
            app.setDefaultProperties(Map.of("server.address", SERVER_LOCAL_ADDR, "server.port", SERVER_PORT));
        }
        ConfigurableApplicationContext ctx = app.run(cliArgs);
        ctx.getBean(SystemInfo.class).logSystemInfo();
        this.springCtx = ctx;
    }

    /**
     * Shuts down the spring application as well as any services that it's using
     */
    //TODO change stop to use services
    public void stop() {
        // TODO: This is untestable with our current design... but do we need to test it really?
        //  (I mean verifying that everything necessary has been stopped)
        if (this.springCtx != null) {
            this.springCtx.close();
        }
    }

}
