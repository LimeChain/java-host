package com.limechain.rpc.subscriptions.chainhead;

import com.limechain.exception.global.ThreadInterruptedException;
import com.limechain.exception.rpc.InvalidURIException;
import com.limechain.rpc.client.SubscriptionRpcClient;
import com.limechain.rpc.config.SubscriptionName;
import com.limechain.rpc.pubsub.Topic;
import com.limechain.rpc.pubsub.publisher.PublisherImpl;
import com.limechain.rpc.subscriptions.utils.Utils;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Implementation for chainHead rpc methods family.
 * <p>
 * For now requests are forwarded to the local running smoldot node since we don't have
 * the services implemented to facilitate the execution of the business logic
 */
@Service
public class ChainHeadRpcImpl implements ChainHeadRpc {
    
    /**
     * WebSocket rpc client which forwards the requests to smoldot
     */
    private final SubscriptionRpcClient rpcClient;

    public ChainHeadRpcImpl(String forwardNodeAddress) {
        try {
            this.rpcClient = new SubscriptionRpcClient(new URI(forwardNodeAddress), new PublisherImpl(),
                    Topic.UNSTABLE_FOLLOW);
            //TODO: Move connect outside constructor
            rpcClient.connectBlocking();
        } catch (URISyntaxException e) {
            throw new InvalidURIException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ThreadInterruptedException(e);
        }
    }

    @Override
    public void chainUnstableFollow(boolean runtimeUpdates) {
        rpcClient.send(SubscriptionName.CHAIN_HEAD_UNSTABLE_FOLLOW.getValue(),
                new String[]{String.valueOf(runtimeUpdates)});
    }

    @Override
    public void chainUnstableUnfollow(String subscriptionId) {
        rpcClient.send(SubscriptionName.CHAIN_HEAD_UNSTABLE_UNFOLLOW.getValue(),
                new String[]{Utils.wrapWithDoubleQuotes(subscriptionId)});
    }

    @Override
    public void chainUnstableUnpin(String subscriptionId, String blockHash) {
        rpcClient.send(SubscriptionName.CHAIN_HEAD_UNSTABLE_UNPIN.getValue(),
                new String[]{Utils.wrapWithDoubleQuotes(subscriptionId), Utils.wrapWithDoubleQuotes(blockHash)});
    }

    @Override
    public void chainUnstableCall(String subscriptionId, String blockHash, String function, String callParameters) {
        rpcClient.send(SubscriptionName.CHAIN_HEAD_UNSTABLE_CALL.getValue(),
                new String[]{Utils.wrapWithDoubleQuotes(subscriptionId), Utils.wrapWithDoubleQuotes(blockHash),
                        Utils.wrapWithDoubleQuotes(function), Utils.wrapWithDoubleQuotes(callParameters)});
    }

    @Override
    public void chainUnstableStorage(String subscriptionId, String blockHash, String key) {
        rpcClient.send(SubscriptionName.CHAIN_HEAD_UNSTABLE_STORAGE.getValue(),
                new String[]{Utils.wrapWithDoubleQuotes(subscriptionId), Utils.wrapWithDoubleQuotes(blockHash),
                        Utils.wrapWithDoubleQuotes(key)});
    }

    @Override
    public void chainUnstableStopCall(String subscriptionId) {
        rpcClient.send(SubscriptionName.CHAIN_HEAD_UNSTABLE_STOP_CALL.getValue(),
                new String[]{Utils.wrapWithDoubleQuotes(subscriptionId)});
    }
}
