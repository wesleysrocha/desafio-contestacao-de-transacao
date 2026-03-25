package com.desafio.prevencao.data.mapper;

import com.desafio.prevencao.data.dto.AuditLogResponse;
import com.desafio.prevencao.data.dto.ContestationResponse;
import com.desafio.prevencao.domain.entity.ContestationAuditLog;
import com.desafio.prevencao.domain.entity.ContestationRequest;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ContestationMapper {

    public ContestationResponse toResponse(ContestationRequest entity) {
        return toResponse(entity, Collections.emptyList());
    }

    public ContestationResponse toResponse(ContestationRequest entity, List<ContestationAuditLog> auditLogs) {
        return ContestationResponse.builder()
                .requestId(entity.getId())
                .contestationId(entity.getContestationId())
                .communicationType(entity.getCommunicationType())
                .communicationStatus(entity.getCommunicationStatus())
                .payload(entity.getPayload())
                .lastError(entity.getLastError())
                .correlationId(entity.getCorrelationId())
                .receivedAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .auditHistory(toAuditLogResponses(auditLogs))
                .build();
    }

    public AuditLogResponse toAuditLogResponse(ContestationAuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .fromStatus(log.getFromStatus())
                .toStatus(log.getToStatus())
                .message(log.getMessage())
                .createdAt(log.getCreatedAt())
                .build();
    }

    public List<AuditLogResponse> toAuditLogResponses(List<ContestationAuditLog> logs) {
        if (logs == null) return Collections.emptyList();
        return logs.stream().map(this::toAuditLogResponse).toList();
    }
}
