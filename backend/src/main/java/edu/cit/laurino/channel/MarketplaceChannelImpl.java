package edu.cit.laurino.channel;

import edu.cit.laurino.InstanceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MarketplaceChannelImpl implements MarketplaceChannel {
    private final InstanceContext instanceContext;
    private final ChannelFeedCursorRepository cursorRepository;

    MarketplaceChannelImpl(InstanceContext instanceContext, ChannelFeedCursorRepository cursorRepository) {
        this.instanceContext = instanceContext;
        this.cursorRepository = cursorRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MarketplaceStatus currentStatus() {
        long lastSeq = cursorRepository.findById((short) 1).map(ChannelFeedCursorEntity::getLastSeq).orElse(0L);
        return new MarketplaceStatus(instanceContext.instanceId(), instanceContext.startedAt(), lastSeq);
    }
}
