package com.limechain.cli;

import com.limechain.storage.DBInitializer;
import org.apache.commons.cli.Options;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliTest {
    private Cli cli;

    @BeforeEach
    public void setup() {
        cli = new Cli();
    }

    @Test
    void buildOptions_buildsOptions() {
        Options options = cli.getOptions();
        assertNotNull(options);
        assertTrue(options.hasOption("network"));
        assertTrue(options.hasOption("n"));
        assertTrue(options.hasOption("db-path"));
        assertTrue(options.hasOption("dbc"));
        assertTrue(options.hasOption("node-key"));
        assertTrue(options.hasOption("node-mode"));
        assertTrue(options.hasOption("mode"));
        assertTrue(options.hasOption("no-legacy-protocols"));
        assertTrue(options.hasOption("sync-mode"));
        assertTrue(options.hasOption("public-rpc"));
        assertTrue(options.hasOption("rpc-methods"));
        assertEquals(0, options.getRequiredOptions().size());
    }

    @Test
    void parseArgs_returns_networkParameter() {
        CliArguments arguments = cli.parseArgs(new String[]{"--network", "polkadot"});
        assertEquals("polkadot", arguments.network());
    }

    @Test
    void parseArgs_returns_defaultLocalUnsafeRpcEnabled() {
        CliArguments arguments = cli.parseArgs(new String[]{});
        assertTrue(arguments.unsafeRpcEnabled());
    }

    @Test
    void parseArgs_returns_defaultPublicSafeRpcEnabled() {
        CliArguments arguments = cli.parseArgs(new String[]{"--public-rpc"});
        assertFalse(arguments.unsafeRpcEnabled());
    }

    @Test
    void parseArgs_returns_localSafeRpcEnabled() {
        CliArguments arguments = cli.parseArgs(new String[]{"--rpc-methods", "safe"});
        assertFalse(arguments.unsafeRpcEnabled());
    }

    @Test
    void parseArgs_returns_publicUnsafeRpcEnabled() {
        CliArguments arguments = cli.parseArgs(new String[]{"--rpc-methods", "unsafe", "--public-rpc"});
        assertTrue(arguments.unsafeRpcEnabled());
    }

    @Test
    void parseArgs_returns_defaultValue() {
        CliArguments arguments = cli.parseArgs(new String[]{});
        assertEquals("", arguments.network());
    }

    @Test
    void parseArgs_returns_shortNetworkParameter() {
        CliArguments arguments = cli.parseArgs(new String[]{"-n", "polkadot"});
        assertEquals("polkadot", arguments.network());
    }

    @Test
    void parseArgs_returns_dbPathParameter() {
        CliArguments arguments = cli.parseArgs(new String[]{"--db-path", "./test-path-somewhere"});
        assertEquals("./test-path-somewhere", arguments.dbPath());
    }

    @Test
    void parseArgs_returns_defaultDbPathParameter() {
        CliArguments arguments = cli.parseArgs(new String[]{});
        assertEquals(DBInitializer.DEFAULT_DIRECTORY, arguments.dbPath());
    }

    @Test
    void parseArgs_throws_whenInvalidArguments() {

        assertThrows(RuntimeException.class, () -> cli.parseArgs(new String[]{"--network"}));

        assertThrows(RuntimeException.class, () -> cli.parseArgs(new String[]{"-network"}));

        assertThrows(RuntimeException.class, () -> cli.parseArgs(new String[]{"--unsupportedParam"}));

    }

}
