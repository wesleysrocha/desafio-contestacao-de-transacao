package com.desafio.prevencao.service;

import com.desafio.prevencao.data.dto.SqsContestationMessage;
import com.desafio.prevencao.domain.entity.ContestationRequest;
import com.desafio.prevencao.domain.enums.ContestationStatus;
import com.desafio.prevencao.repositories.ContestationRequestRepository;
import com.desafio.prevencao.resourses.client.CommDispatcherClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço responsável pelo processamento assíncrono das mensagens de contestation-requests.
 * Chama o serviço externo de disparo (mock-comm-dispatcher via HTTP).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContestationProcessingService {

    private final ContestationRequestRepository requestRepository;
    private final CommDispatcherClient commDispatcherClient;
    private final ContestationService contestationService;

    @Transactional
    public void processContestationRequest(SqsContestationMessage message) {
        log.info("[PROCESSING] Iniciando processamento requestId={}", message.getRequestId());

        ContestationRequest entity = requestRepository.findById(message.getRequestId())
                .orElse(null);

        if (entity == null) {
            log.warn("[PROCESSING] Contestação não encontrada no banco para requestId={}", message.getRequestId());
            return;
        }

        // Se já foi cancelada, não processa
        if (entity.getCommunicationStatus() == ContestationStatus.CANCELADO) {
            log.info("[PROCESSING] Contestação cancelada, ignorando processamento requestId={}", message.getRequestId());
            return;
        }

        try {
            // Chama o serviço externo de disparo
            commDispatcherClient.dispatch(message);

            log.info("[PROCESSING] Despacho enviado para serviço externo requestId={}", message.getRequestId());

        } catch (Exception ex) {
            log.error("[PROCESSING] Erro ao chamar comm-dispatcher para requestId={}: {}",
                    message.getRequestId(), ex.getMessage(), ex);

            entity.setLastError("Erro no dispatch: " + ex.getMessage());
            requestRepository.save(entity);

            contestationService.saveAuditLog(entity.getId(),
                    ContestationStatus.EM_ANDAMENTO, ContestationStatus.EM_ANDAMENTO,
                    "Erro ao chamar comm-dispatcher: " + ex.getMessage());

            // Re-lança para que SQS trate e envie para DLQ se necessário
            throw new RuntimeException("Falha ao processar contestação: " + ex.getMessage(), ex);
        }
    }
}
