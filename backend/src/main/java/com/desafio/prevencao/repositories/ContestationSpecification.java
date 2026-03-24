package com.desafio.prevencao.repositories;

import com.desafio.prevencao.domain.entity.ContestationRequest;
import com.desafio.prevencao.domain.enums.ContestationStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ContestationSpecification {

    private ContestationSpecification() {}

    public static Specification<ContestationRequest> withFilters(
            ContestationStatus status,
            String contestationId,
            LocalDateTime fromDate,
            LocalDateTime toDate) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("communicationStatus"), status));
            }

            if (contestationId != null && !contestationId.isBlank()) {
                predicates.add(cb.equal(root.get("contestationId"), contestationId));
            }

            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }

            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
