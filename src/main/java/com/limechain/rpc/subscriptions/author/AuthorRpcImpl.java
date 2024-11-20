package com.limechain.rpc.subscriptions.author;

import com.limechain.exception.global.ExecutionFailedException;
import com.limechain.exception.global.ThreadInterruptedException;
import com.limechain.exception.rpc.InvalidURIException;
import com.limechain.exception.transaction.TransactionValidationException;
import com.limechain.rpc.client.SubscriptionRpcClient;
import com.limechain.rpc.config.SubscriptionName;
import com.limechain.rpc.pubsub.Topic;
import com.limechain.rpc.pubsub.publisher.PublisherImpl;
import com.limechain.rpc.subscriptions.utils.Utils;
import com.limechain.transaction.TransactionProcessor;
import com.limechain.transaction.dto.Extrinsic;
import com.limechain.utils.StringUtils;
import com.limechain.utils.scale.ScaleUtils;
import io.emeraldpay.polkaj.scale.ScaleCodecReader;

import java.net.URI;
import java.net.URISyntaxException;

public class AuthorRpcImpl implements AuthorRpc {

    private final SubscriptionRpcClient rpcClient;
    private final TransactionProcessor transactionProcessor;

    public AuthorRpcImpl(String forwardNodeAddress, TransactionProcessor transactionProcessor) {
        this.transactionProcessor = transactionProcessor;

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
        Extrinsic decodedExtrinsic = new Extrinsic(
                ScaleUtils.Decode.decode(
                        StringUtils.hexToBytes(extrinsic),
                        ScaleCodecReader::readByteArray
                )
        );

        try {
            transactionProcessor.handlePoolOnlyExternalTransaction(decodedExtrinsic);
        } catch (TransactionValidationException e) {
            throw new ExecutionFailedException("Failed to executed submit_extrinsic call: " + e.getMessage());
        }

        rpcClient.send(SubscriptionName.AUTHOR_SUBMIT_AND_WATCH_EXTRINSIC.getValue(),
                new String[]{Utils.wrapWithDoubleQuotes(extrinsic)});
    }
}
