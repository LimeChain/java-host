package com.limechain.rpc.subscriptions.transaction;

import com.limechain.exception.global.ThreadInterruptedException;
import com.limechain.exception.rpc.InvalidURIException;
import com.limechain.rpc.client.SubscriptionRpcClient;
import com.limechain.rpc.config.SubscriptionName;
import com.limechain.rpc.pubsub.Topic;
import com.limechain.rpc.pubsub.publisher.PublisherImpl;
import com.limechain.rpc.subscriptions.utils.Utils;

import java.net.URI;
import java.net.URISyntaxException;

public class TransactionRpcImpl implements TransactionRpc {

    /**
     * WebSocket client which forwards the requests to smoldot
     */
    private final SubscriptionRpcClient rpcClient;

    public TransactionRpcImpl(String forwardNodeAddress) {
        try {
            this.rpcClient = new SubscriptionRpcClient(new URI(forwardNodeAddress), new PublisherImpl(),
                    Topic.UNSTABLE_TRANSACTION_WATCH);
            // TODO: Move connect outside constructor
            rpcClient.connectBlocking();
        } catch (URISyntaxException e) {
            throw new InvalidURIException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ThreadInterruptedException(e);
        }
    }

    @Override
    public void transactionUnstableSubmitAndWatch(String transaction) {
        rpcClient.send(SubscriptionName.TRANSACTION_UNSTABLE_SUBMIT_AND_WATCH.getValue(),
                new String[]{Utils.wrapWithDoubleQuotes(transaction)});
    }

    @Override
    public void transactionUnstableUnwatch(String subscriptionId) {
        rpcClient.send(SubscriptionName.TRANSACTION_UNSTABLE_UNWATCH.getValue(),
                new String[]{Utils.wrapWithDoubleQuotes(subscriptionId)});
    }
}
