package com.desafio.prevencao.service;

import com.desafio.prevencao.data.dto.*;
import com.desafio.prevencao.data.mapper.ContestationMapper;
import com.desafio.prevencao.domain.entity.ContestationAuditLog;
import com.desafio.prevencao.domain.entity.ContestationRequest;
import com.desafio.prevencao.domain.enums.ContestationStatus;
import com.desafio.prevencao.domain.enums.ContestationType;
import com.desafio.prevencao.middleware.CorrelationIdFilter;
import com.desafio.prevencao.middleware.exception.BusinessException;
import com.desafio.prevencao.middleware.exception.ConflictException;
import com.desafio.prevencao.middleware.exception.ResourceNotFoundException;
import com.desafio.prevencao.repositories.ContestationAuditLogRepository;
import com.desafio.prevencao.repositories.ContestationRequestRepository;
import com.desafio.prevencao.repositories.ContestationSpecification;
import com.desafio.prevencao.resourses.sqs.ContestationRequestProducer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContestationService {

    private final ContestationRequestRepository requestRepository;
    private final ContestationAuditLogRepository auditLogRepository;
    private final ContestationRequestProducer producer;
    private final ContestationMapper mapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public CreateContestationResponse createContestation(CreateContestationRequest request) {
        String correlationId = CorrelationIdFilter.getCurrentCorrelationId();

        // Valida unicidade do contestationId
        if (requestRepository.existsByContestationId(request.getContestationId())) {
            throw new ConflictException(
                    "Já existe uma contestação com contestationId: " + request.getContestationId());
        }

        // Serializa dados adicionais como payload JSON
        String payloadJson = serializePayload(request);

        // Persiste a contestação com status EM_ANDAMENTO
        ContestationRequest entity = ContestationRequest.builder()
                .contestationId(request.getContestationId())
                .communicationType(ContestationType.CONTESTACAO_ABERTA)
                .communicationStatus(ContestationStatus.EM_ANDAMENTO)
                .payload(payloadJson)
                .correlationId(correlationId)
                .build();

        entity = requestRepository.save(entity);

        // Registra auditoria de criação
        saveAuditLog(entity.getId(), null, ContestationStatus.EM_ANDAMENTO,
                "Contestação criada e enfileirada para processamento");

        // Enfileira na SQS
        SqsContestationMessage sqsMessage = SqsContestationMessage.builder()
                .requestId(entity.getId())
                .contestationId(entity.getContestationId())
                .communicationType(entity.getCommunicationType())
                .payload(entity.getPayload())
                .correlationId(correlationId)
                .build();

        producer.send(sqsMessage);

        log.info("[SERVICE] Contestação criada com sucesso. requestId={} contestationId={}",
                entity.getId(), entity.getContestationId());

        return CreateContestationResponse.builder()
                .requestId(entity.getId())
                .status(entity.getCommunicationStatus())
                .receivedAt(entity.getCreatedAt())
                .correlationId(correlationId)
                .idempotent(false)
                .build();
    }

    @Transactional(readOnly = true)
    public ContestationResponse findById(String requestId) {
        ContestationRequest entity = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Contestação", requestId));

        List<ContestationAuditLog> auditLogs = auditLogRepository
                .findByRequestIdOrderByCreatedAtAsc(requestId);

        return mapper.toResponse(entity, auditLogs);
    }

    @Transactional(readOnly = true)
    public Page<ContestationResponse> listContestations(
            int page, int size,
            ContestationStatus status,
            String contestationId,
            LocalDateTime fromDate,
            LocalDateTime toDate) {

        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return requestRepository
                .findAll(ContestationSpecification.withFilters(
                        status, contestationId, fromDate, toDate), pageRequest)
                .map(mapper::toResponse);
    }

    @Transactional
    public ContestationResponse cancelContestation(String requestId) {
        ContestationRequest entity = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Contestação", requestId));

        if (entity.getCommunicationStatus() == ContestationStatus.CANCELADO) {
            log.info("[SERVICE] Cancelamento idempotente para requestId={}", requestId);
            return mapper.toResponse(entity);
        }

        if (entity.getCommunicationStatus() != ContestationStatus.EM_ANDAMENTO) {
            throw new BusinessException(
                    "Não é possível cancelar uma contestação com status: "
                            + entity.getCommunicationStatus().name());
        }

        ContestationStatus previousStatus = entity.getCommunicationStatus();
        entity.setCommunicationStatus(ContestationStatus.CANCELADO);
        entity = requestRepository.save(entity);

        saveAuditLog(entity.getId(), previousStatus, ContestationStatus.CANCELADO,
                "Cancelamento solicitado pelo usuário");

        log.info("[SERVICE] Contestação cancelada requestId={}", requestId);
        return mapper.toResponse(entity);
    }

    @Transactional
    public void replayContestation(String requestId) {
        ContestationRequest entity = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Contestação", requestId));

        ContestationStatus previousStatus = entity.getCommunicationStatus();

        entity.setCommunicationStatus(ContestationStatus.EM_ANDAMENTO);
        entity.setLastError(null);
        requestRepository.save(entity);

        SqsContestationMessage sqsMessage = SqsContestationMessage.builder()
                .requestId(entity.getId())
                .contestationId(entity.getContestationId())
                .communicationType(entity.getCommunicationType())
                .payload(entity.getPayload())
                .correlationId(entity.getCorrelationId())
                .build();

        producer.send(sqsMessage);

        saveAuditLog(entity.getId(), previousStatus, ContestationStatus.EM_ANDAMENTO,
                "Replay manual solicitado via admin endpoint");

        log.info("[SERVICE] Replay iniciado para requestId={}", requestId);
    }

    public void saveAuditLog(String requestId, ContestationStatus from, ContestationStatus to, String message) {
        ContestationAuditLog log = ContestationAuditLog.builder()
                .requestId(requestId)
                .fromStatus(from)
                .toStatus(to)
                .message(message)
                .build();
        auditLogRepository.save(log);
    }

    private String serializePayload(CreateContestationRequest request) {
        try {
            Map<String, Object> payload = new java.util.HashMap<>(request.getAdditionalData());
            if (request.getAmount() != null) {
                payload.put("amount", request.getAmount());
            }
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Erro ao serializar payload: {}", e.getMessage());
            return "{}";
        }
    }
}
