package com.desafio.prevencao.service;

import com.desafio.prevencao.data.dto.SqsResultMessage;
import com.desafio.prevencao.domain.entity.ContestationRequest;
import com.desafio.prevencao.domain.enums.ContestationStatus;
import com.desafio.prevencao.repositories.ContestationRequestRepository;
import com.desafio.prevencao.resourses.client.ContestationCallbackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContestationCallbackService {

    private final ContestationRequestRepository requestRepository;
    private final ContestationCallbackClient callbackClient;
    private final ContestationService contestationService;

    @Transactional
    public void processResult(SqsResultMessage message) {
        log.info("[CALLBACK-SERVICE] Processando resultado requestId={} success={}",
                message.getRequestId(), message.isSuccess());

        ContestationRequest entity = requestRepository.findById(message.getRequestId())
                .orElse(null);

        if (entity == null) {
            log.warn("[CALLBACK-SERVICE] Contestação não encontrada para requestId={}", message.getRequestId());
            return;
        }

        if (isTerminalStatus(entity.getCommunicationStatus())) {
            log.info("[CALLBACK-SERVICE] Contestação já em estado terminal {}. Ignorando. requestId={}",
                    entity.getCommunicationStatus(), message.getRequestId());
            return;
        }

        // Executa callback para o sistema de contestações
        boolean callbackSuccess = callbackClient.executeCallback(message);

        ContestationStatus previousStatus = entity.getCommunicationStatus();
        ContestationStatus newStatus;
        String auditMessage;

        if (callbackSuccess) {
            newStatus = ContestationStatus.SUCESSO;
            auditMessage = "Processamento concluído. Callback executado com sucesso.";
            entity.setLastError(null);
        } else {
            newStatus = ContestationStatus.CALLBACK_FALHA;
            auditMessage = "Callback falhou após todas as tentativas. " +
                    "ResultDetails: " + message.getResultDetails();
            entity.setLastError("Callback falhou após retries esgotados. Detalhes: " + message.getResultDetails());
        }

        entity.setCommunicationStatus(newStatus);
        requestRepository.save(entity);

        contestationService.saveAuditLog(entity.getId(), previousStatus, newStatus, auditMessage);

        log.info("[CALLBACK-SERVICE] Status atualizado para {} requestId={}",
                newStatus, message.getRequestId());
    }

    private boolean isTerminalStatus(ContestationStatus status) {
        return status == ContestationStatus.SUCESSO
                || status == ContestationStatus.CANCELADO;
    }
}
