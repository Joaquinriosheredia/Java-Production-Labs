package com.labs.saga.service;

import com.labs.saga.model.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    long countBySagaStatus(PurchaseOrder.SagaStatus status);
}
