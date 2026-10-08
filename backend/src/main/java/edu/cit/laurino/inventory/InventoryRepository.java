package edu.cit.laurino.inventory;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface InventoryRepository extends JpaRepository<InventoryEntity, String> {
    /**
     * Locked lookup for the actual read-modify-write sequence in reserve()
     * and restock() - this is the only place a write lock belongs. Plain
     * findById() (inherited, unlocked) is for everything else, including
     * genuinely read-only lookups like getItem(). Locking every findById()
     * call, as a previous version of this interface did, breaks any
     * read-only transaction that calls it - PostgreSQL refuses to run a
     * locking SELECT inside a read-only transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryEntity i where i.productId = :productId")
    Optional<InventoryEntity> findForUpdate(String productId);
}
