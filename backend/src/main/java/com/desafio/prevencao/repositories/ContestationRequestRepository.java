package com.desafio.prevencao.repositories;

import com.desafio.prevencao.domain.entity.ContestationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ContestationRequestRepository
        extends JpaRepository<ContestationRequest, String>, JpaSpecificationExecutor<ContestationRequest> {

    boolean existsByContestationId(String contestationId);
}
