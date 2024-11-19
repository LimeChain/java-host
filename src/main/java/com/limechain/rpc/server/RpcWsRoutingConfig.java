package com.limechain.rpc.server;

import com.limechain.config.HostConfig;
import com.limechain.rpc.methods.RPCMethods;
import com.limechain.rpc.methods.author.AuthorRPC;
import com.limechain.rpc.subscriptions.author.AuthorRpcImpl;
import com.limechain.rpc.subscriptions.chainhead.ChainHeadRpc;
import com.limechain.rpc.subscriptions.chainhead.ChainHeadRpcImpl;
import com.limechain.rpc.subscriptions.transaction.TransactionRpc;
import com.limechain.rpc.subscriptions.transaction.TransactionRpcImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Additional Spring configuration class for the ws rpc server
 */
@Configuration
@EnableWebSocket
public class RpcWsRoutingConfig implements WebSocketConfigurer {

    /**
     * Dependencies will be injected from {@link com.limechain.rpc.config.CommonConfig}
     */
    private final RPCMethods rpcMethods;
    private final HostConfig hostConfig;
    private final AuthorRPC authorRPC;

    public RpcWsRoutingConfig(RPCMethods rpcMethods, HostConfig hostConfig, AuthorRPC authorRPC) {
        this.rpcMethods = rpcMethods;
        this.hostConfig = hostConfig;
        this.authorRPC = authorRPC;
    }

    /**
     * Exposes ws routing handler on "/" route
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler(), "/");
    }

    /**
     * The handler that will be executed when ws rpc request is received
     */
    public RpcWsHandler webSocketHandler() {
        return new RpcWsHandler(rpcMethods, chainHeadRpc(), transactionRpc(), authorRpc());
    }

    /**
     * Additional beans used by {@link RpcWsHandler}
     */
    @Bean
    public ChainHeadRpc chainHeadRpc() {
        return new ChainHeadRpcImpl(hostConfig.getRpcNodeAddress());
    }

    @Bean
    public TransactionRpc transactionRpc() {
        return new TransactionRpcImpl(hostConfig.getRpcNodeAddress());
    }

    @Bean
    public AuthorRpcImpl authorRpc() {
        return new AuthorRpcImpl(hostConfig.getRpcNodeAddress(), authorRPC);
    }

}
