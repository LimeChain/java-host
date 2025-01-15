package com.limechain.rpc.subscriptions.chainsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limechain.network.protocol.warp.dto.BlockHeader;
import com.limechain.rpc.methods.chain.ChainRPCImpl;
import com.limechain.rpc.pubsub.Message;
import com.limechain.rpc.pubsub.PubSubService;
import com.limechain.rpc.pubsub.Topic;
import com.limechain.rpc.pubsub.messages.JsonRpcWsResponseMessage;
import com.limechain.rpc.server.AppBean;
import com.limechain.storage.block.state.BlockState;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;

import java.util.Map;

@Log
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class ChainSub {

    private static final ChainSub INSTANCE = new ChainSub();
    private final PubSubService pubSubService = PubSubService.getInstance();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockState blockState = AppBean.getBean(BlockState.class);

    /**
     * Gets the singleton reference
     */
    public static ChainSub getInstance() {
        return INSTANCE;
    }

    /**
     * Notifies subscribers about a new chain head.
     * It sends notifications for all new blocks and for new blocks that are considered the best block.
     *
     * @param blockHeader the block header of the new chain head.
     */
    public void notifyNewChainHead(final BlockHeader blockHeader) {
        //CHAIN_ALL_HEAD listens to all new blocks
        final Message allHeadMsg = createMessageFromHeader(Topic.CHAIN_ALL_HEAD, blockHeader);
        pubSubService.addMessageToQueue(allHeadMsg);

        if (blockState.bestBlockHash().equals(blockHeader.getHash())) {
            //CHAIN_NEW_HEAD listens for the new block that is considered the best block ( highest number )
            final Message newHeadMsg = createMessageFromHeader(Topic.CHAIN_NEW_HEAD, blockHeader);
            pubSubService.addMessageToQueue(newHeadMsg);
        }

        pubSubService.broadcast();
        pubSubService.notifySubscribers();
    }

    /**
     * Notifies subscribers about a finalized chain head.
     *
     * @param blockHeader the block header of the finalized chain head.
     */
    public void notifyFinalizedChainHead(final BlockHeader blockHeader) {
        //CHAIN_FINALIZED_HEAD listens for the new block that is finalized
        final Message message = createMessageFromHeader(Topic.CHAIN_FINALIZED_HEAD, blockHeader);
        pubSubService.addMessageToQueue(message);

        pubSubService.broadcast();
        pubSubService.notifySubscribers();
    }

    /**
     * Creates a message from a block header and a topic.
     * This method serializes the block header into a JSON string and wraps it into a message.
     *
     * @param topic       the topic under which the message should be published.
     * @param blockHeader the block header to be serialized and included in the message.
     * @return a new Message instance containing the serialized block header, or null if serialization fails.
     */
    private Message createMessageFromHeader(final Topic topic, final BlockHeader blockHeader) {
        Map<String, Object> blockHeaderMap = ChainRPCImpl.headerToMap(blockHeader);
        try {
            final String blockHeaderStr = objectMapper.writeValueAsString(blockHeaderMap);
            final var resp = new JsonRpcWsResponseMessage(blockHeaderStr, topic.toString());
            return new Message(topic, resp.toString());
        } catch (JsonProcessingException e) {
            log.warning("Failed to convert block header map to JSON string");
            return null;
        }
    }
}