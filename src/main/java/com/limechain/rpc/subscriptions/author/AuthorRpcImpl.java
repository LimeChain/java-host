package com.limechain.rpc.subscriptions.author;

import com.limechain.exception.global.ThreadInterruptedException;
import com.limechain.exception.rpc.InvalidURIException;
import com.limechain.rpc.client.SubscriptionRpcClient;
import com.limechain.rpc.config.SubscriptionName;
import com.limechain.rpc.pubsub.Topic;
import com.limechain.rpc.pubsub.publisher.PublisherImpl;
import com.limechain.rpc.subscriptions.utils.Utils;

import java.net.URI;
import java.net.URISyntaxException;

//TODO: Many of the other WS RPC methods call smalldot in order to generate data
// The methods that don't call smalldot have triggers which sends updates through the ws connection -> com/limechain/rpc/subscriptions/chainsub/ChainSub.java:39
// Documentation for author_submitAndWatchExtrinsic:
// 1. PSP -> https://github.com/w3f/PSPs/blob/master/PSPs/drafts/psp-6.md#189-author_submitandwatchextrinsic-pubsub
// 2. polkadot js -> https://polkadot.js.org/docs/substrate/rpc/#submitandwatchextrinsicextrinsic-extrinsic-extrinsicstatus
public class AuthorRpcImpl implements AuthorRpc {

    private final SubscriptionRpcClient rpcClient;

    public AuthorRpcImpl(String forwardNodeAddress) {
        try {
            this.rpcClient = new SubscriptionRpcClient(new URI(forwardNodeAddress), new PublisherImpl(),
                    Topic.AUTHOR_EXTRINSIC_UPDATE);
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
    public void authorSubmitAndWatchExtrinsic(String extrinsic) {
        //TODO: Call somehow the already implemented authorSubmitExtrinsic from the other AuthorRPC class.
        // The result should be propagated to the parameters of the rpcClient.send(..., HERE) method
        rpcClient.send(SubscriptionName.AUTHOR_SUBMIT_AND_WATCH_EXTRINSIC.getValue(),
                new String[]{Utils.wrapWithDoubleQuotes(extrinsic)});
    }
}
