package edu.cit.laurino.channel;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ChannelOrderRepository extends JpaRepository<ChannelOrderEntity, String> {
    Optional<ChannelOrderEntity> findByTiangeOrderId(String tiangeOrderId);

    List<ChannelOrderEntity> findByDecision(ChannelDecision decision);

    List<ChannelOrderEntity> findBySyncedFalse();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChannelOrderEntity c where c.tiangeOrderId = :tiangeOrderId")
    Optional<ChannelOrderEntity> findForUpdate(String tiangeOrderId);
}
