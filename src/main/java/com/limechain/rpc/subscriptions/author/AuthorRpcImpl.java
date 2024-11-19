package com.limechain.rpc.subscriptions.author;

import com.limechain.exception.global.ThreadInterruptedException;
import com.limechain.exception.rpc.InvalidURIException;
import com.limechain.rpc.client.SubscriptionRpcClient;
import com.limechain.rpc.config.SubscriptionName;
import com.limechain.rpc.methods.author.AuthorRPC;
import com.limechain.rpc.pubsub.Topic;
import com.limechain.rpc.pubsub.publisher.PublisherImpl;
import com.limechain.rpc.subscriptions.utils.Utils;

import java.net.URI;
import java.net.URISyntaxException;

public class AuthorRpcImpl implements AuthorRpc {

    private final SubscriptionRpcClient rpcClient;
    private final AuthorRPC authorRPC;

    public AuthorRpcImpl(String forwardNodeAddress, AuthorRPC authorRPC) {

        this.authorRPC = authorRPC;
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
        authorRPC.authorSubmitExtrinsic(extrinsic);
        rpcClient.send(SubscriptionName.AUTHOR_SUBMIT_AND_WATCH_EXTRINSIC.getValue(),
                new String[]{Utils.wrapWithDoubleQuotes(extrinsic)});
    }
}
