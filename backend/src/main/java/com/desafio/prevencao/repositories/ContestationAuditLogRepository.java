package com.desafio.prevencao.repositories;

import com.desafio.prevencao.domain.entity.ContestationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContestationAuditLogRepository extends JpaRepository<ContestationAuditLog, String> {

    List<ContestationAuditLog> findByRequestIdOrderByCreatedAtAsc(String requestId);
}
