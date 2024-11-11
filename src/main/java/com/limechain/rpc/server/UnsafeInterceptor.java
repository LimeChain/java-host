package com.limechain.rpc.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.googlecode.jsonrpc4j.JsonRpcInterceptor;
import com.limechain.cli.CliArguments;
import com.limechain.exception.rpc.UnsafeAccessException;
import com.limechain.rpc.config.UnsafeRpcMethod;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Interceptor for JSON-RPC calls that checks for unsafe method access.
 * It prevents the execution of methods annotated with {@link UnsafeRpcMethod}
 * unless the application is running in an unsafe mode.
 */
public class UnsafeInterceptor implements JsonRpcInterceptor {
    private CliArguments cliArguments;

    @Override
    public void preHandleJson(JsonNode jsonNode) {
        // Do nothing
    }

    @Override
    public void preHandle(Object o, Method method, List<JsonNode> list) {
        if (this.cliArguments == null) {
            this.cliArguments = AppBean.getBean(CliArguments.class);
        }

        // Check if the method is marked as unsafe and  unsafe RPC is not enabled
        if (method.isAnnotationPresent(UnsafeRpcMethod.class) && !this.cliArguments.unsafeRpcEnabled()) {
            throw new UnsafeAccessException(
                    "Unsafe mode is not enabled. This method is only available in unsafe mode.");

        }
    }

    @Override
    public void postHandle(Object o, Method method, List<JsonNode> list, JsonNode jsonNode) {
        // Do nothing
    }

    @Override
    public void postHandleJson(JsonNode jsonNode) {
        // Do nothing
    }
}
