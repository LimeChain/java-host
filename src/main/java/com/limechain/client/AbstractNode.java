package com.limechain.client;

import com.limechain.NodeService;
import com.limechain.ServiceState;

import java.util.List;

public abstract class AbstractNode extends HostNode {

    public AbstractNode(List<NodeService> services, List<ServiceState> states) {
        super(services, states);
    }
}
