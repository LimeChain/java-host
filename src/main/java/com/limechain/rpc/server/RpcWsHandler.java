package com.limechain.rpc.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.JsonRpcBasicServer;
import com.limechain.rpc.config.SubscriptionName;
import com.limechain.rpc.methods.RPCMethods;
import com.limechain.rpc.pubsub.PubSubService;
import com.limechain.rpc.pubsub.Topic;
import com.limechain.rpc.pubsub.messages.JsonRpcWsErrorMessage;
import com.limechain.rpc.pubsub.subscriberchannel.Subscriber;
import com.limechain.rpc.subscriptions.author.AuthorRpc;
import com.limechain.rpc.subscriptions.chainhead.ChainHeadRpc;
import com.limechain.rpc.subscriptions.transaction.TransactionRpc;
import lombok.extern.java.Log;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.coyote.InputBuffer;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

@Component
@Log
public class RpcWsHandler extends TextWebSocketHandler {

    private final JsonRpcBasicServer server;
    private final PubSubService pubSubService = PubSubService.getInstance();
    private final ObjectMapper mapper;
    private final ChainHeadRpc chainHeadRpc;
    private final TransactionRpc transactionRpc;
    private final AuthorRpc authorRpc;

    public RpcWsHandler(RPCMethods rpcMethods,
                        ChainHeadRpc chainHeadRpc,
                        TransactionRpc transactionRpc,
                        AuthorRpc authorRpc) {

        this.mapper = new ObjectMapper();
        this.server = new JsonRpcBasicServer(mapper, rpcMethods, RPCMethods.class);
        this.chainHeadRpc = chainHeadRpc;
        this.transactionRpc = transactionRpc;
        this.authorRpc = authorRpc;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        InputStream messageStream = new ByteArrayInputStream(message.asBytes());
        RpcRequest rpcRequest = mapper.readValue(messageStream, RpcRequest.class);
        log.log(Level.INFO, "SESSION ID: " + session.getId());
        log.log(Level.INFO, "METHOD: " + rpcRequest.getMethod());
        log.log(Level.INFO, "PARAMS: " + String.join(",", rpcRequest.getParams()));

        SubscriptionName method = SubscriptionName.fromString(rpcRequest.getMethod());
        if (method == null) {
            log.log(Level.INFO, "Handling the WS request using the normal RPC routes");
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Request request = this.buildHttpLikeRequest(message.asBytes());
            server.handleRequest(request.getInputStream(), outputStream);
            session.sendMessage(new TextMessage(outputStream.toByteArray()));
            return;
        }

        switch (method) {
            case CHAIN_SUBSCRIBE_ALL_HEADS -> handleDefaultSubscribe(Topic.CHAIN_ALL_HEAD, session);
            case CHAIN_UNSUBSCRIBE_ALL_HEADS -> handleDefaultUnsubscribe(Topic.CHAIN_ALL_HEAD, rpcRequest, session);
            case CHAIN_SUBSCRIBE_NEW_HEADS -> handleDefaultSubscribe(Topic.CHAIN_NEW_HEAD, session);
            case CHAIN_UNSUBSCRIBE_NEW_HEADS -> handleDefaultUnsubscribe(Topic.CHAIN_NEW_HEAD, rpcRequest, session);
            case CHAIN_SUBSCRIBE_FINALIZED_HEADS -> handleDefaultSubscribe(Topic.CHAIN_FINALIZED_HEAD, session);
            case STATE_SUBSCRIBE_RUNTIME_VERSION -> handleDefaultUnsubscribe(Topic.STATE_RUNTIME_VERSION, rpcRequest, session);
            case STATE_UNSUBSCRIBE_RUNTIME_VERSION -> handleDefaultSubscribe(Topic.STATE_RUNTIME_VERSION, session);
            case STATE_SUBSCRIBE_STORAGE -> handleDefaultUnsubscribe(Topic.STATE_STORAGE, rpcRequest, session);
            case STATE_UNSUBSCRIBE_STORAGE -> handleDefaultSubscribe(Topic.STATE_STORAGE, session);
            case CHAIN_UNSUBSCRIBE_FINALIZED_HEADS ->
                    handleDefaultUnsubscribe(Topic.CHAIN_FINALIZED_HEAD, rpcRequest, session);

            case CHAIN_HEAD_UNSTABLE_FOLLOW -> {
                log.log(Level.INFO, "Subscribing for follow event");
                pubSubService.addSubscriber(Topic.UNSTABLE_FOLLOW, session);
                // This is temporary in order to simulate that our node "processes" blocks
                this.chainHeadRpc.chainUnstableFollow(Boolean.parseBoolean(rpcRequest.getParams()[0]));
            }
            case CHAIN_HEAD_UNSTABLE_UNFOLLOW -> {
                log.log(Level.INFO, "Unsubscribing from follow event");
                this.chainHeadRpc.chainUnstableUnfollow(rpcRequest.getParams()[0]);
                pubSubService.removeSubscriber(Topic.UNSTABLE_FOLLOW, rpcRequest.getParams()[0]);
            }
            case CHAIN_HEAD_UNSTABLE_UNPIN -> {
                log.log(Level.INFO, "Unpinning block");
                this.chainHeadRpc.chainUnstableUnpin(rpcRequest.getParams()[0], rpcRequest.getParams()[1]);
            }
            case CHAIN_HEAD_UNSTABLE_STORAGE -> {
                log.log(Level.INFO, "Querying storage");
                this.chainHeadRpc.chainUnstableStorage(rpcRequest.getParams()[0], rpcRequest.getParams()[1],
                        rpcRequest.getParams()[2]);
            }
            case CHAIN_HEAD_UNSTABLE_CALL -> {
                log.log(Level.INFO, "Executing unstable_call");
                this.chainHeadRpc.chainUnstableCall(rpcRequest.getParams()[0], rpcRequest.getParams()[1],
                        rpcRequest.getParams()[2], rpcRequest.getParams()[3]);
            }
            case CHAIN_HEAD_UNSTABLE_STOP_CALL -> {
                log.log(Level.INFO, "Executing unstable_stopCall");
                this.chainHeadRpc.chainUnstableStopCall(rpcRequest.getParams()[0]);
            }
            case TRANSACTION_UNSTABLE_SUBMIT_AND_WATCH -> {
                log.log(Level.INFO, "Executing submitAndWatch");
                pubSubService.addSubscriber(Topic.UNSTABLE_TRANSACTION_WATCH, session);
                this.transactionRpc.transactionUnstableSubmitAndWatch(rpcRequest.getParams()[0]);
            }
            case TRANSACTION_UNSTABLE_UNWATCH -> {
                log.log(Level.INFO, "Executing unstable_unwatch");
                this.transactionRpc.transactionUnstableUnwatch(rpcRequest.getParams()[0]);
                pubSubService.removeSubscriber(Topic.UNSTABLE_TRANSACTION_WATCH, rpcRequest.getParams()[0]);
            }
            case AUTHOR_SUBMIT_AND_WATCH_EXTRINSIC -> {
                log.log(Level.INFO, "Executing submit and watch extrinsic");
                this.authorRpc.authorSubmitAndWatchExtrinsic(rpcRequest.getParams()[0]);
                pubSubService.addSubscriber(Topic.AUTHOR_EXTRINSIC_UPDATE, session);
            }
            default -> log.log(Level.WARNING, "Unknown method: " + rpcRequest.getMethod());
        }
    }

    private void handleDefaultUnsubscribe(Topic topic, RpcRequest rpcRequest, WebSocketSession session) {
        log.log(Level.INFO, "Unsubscribing for " + topic.getValue());
        boolean remove = pubSubService.removeSubscriber(topic, rpcRequest.getParams()[0]);
        pubSubService.sendResultMessage(session, Boolean.toString(remove));
    }

    private void handleDefaultSubscribe(Topic topic, WebSocketSession session) throws IOException {
        log.log(Level.INFO, "Subscribing for " + topic.getValue());
        Subscriber subscriber = pubSubService.addSubscriber(topic, session);
        if (subscriber != null) pubSubService.sendResultMessage(session, subscriber.getSubscriptionId());
        else {
            JsonRpcWsErrorMessage subError = new JsonRpcWsErrorMessage(0, "Already subscribed", null);
            session.sendMessage(new TextMessage(subError.toString()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // TODO: Search PubSubService for subscribers that have the closed session id
        // as a subscriber and remove it from the their subscription list
    }

    private Request buildHttpLikeRequest(byte[] message) {
        InputBuffer inputBuffer = new SimpleInputBuffer(new ByteArrayInputStream(message));

        org.apache.coyote.Request coyReq = new org.apache.coyote.Request();
        coyReq.setResponse(new org.apache.coyote.Response());
        coyReq.setInputBuffer(inputBuffer);

        Request request = new Request(new Connector());
        request.setCoyoteRequest(coyReq);
        return request;
    }
}
