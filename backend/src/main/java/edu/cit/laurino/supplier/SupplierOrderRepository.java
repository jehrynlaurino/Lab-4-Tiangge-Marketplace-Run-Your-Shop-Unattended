package edu.cit.laurino.supplier;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SupplierOrderRepository extends JpaRepository<SupplierOrderEntity, Long> {
    List<SupplierOrderEntity> findByStatus(SupplierOrderStatus status);

    List<SupplierOrderEntity> findByStatusIn(List<SupplierOrderStatus> statuses);

    boolean existsByProductIdAndStatusIn(String productId, List<SupplierOrderStatus> statuses);
}
