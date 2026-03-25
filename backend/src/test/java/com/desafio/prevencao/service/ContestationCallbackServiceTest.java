package com.desafio.prevencao.service;

import com.desafio.prevencao.data.dto.SqsResultMessage;
import com.desafio.prevencao.domain.entity.ContestationAuditLog;
import com.desafio.prevencao.domain.entity.ContestationRequest;
import com.desafio.prevencao.domain.enums.ContestationStatus;
import com.desafio.prevencao.domain.enums.ContestationType;
import com.desafio.prevencao.repositories.ContestationRequestRepository;
import com.desafio.prevencao.resourses.client.ContestationCallbackClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContestationCallbackService - Testes Unitários")
class ContestationCallbackServiceTest {

    @Mock
    private ContestationRequestRepository requestRepository;
    @Mock
    private ContestationCallbackClient callbackClient;
    @Mock
    private ContestationService contestationService;

    @InjectMocks
    private ContestationCallbackService callbackService;

    @Nested
    @DisplayName("processResult - sucesso")
    class ProcessResultSucesso {

        @Test
        @DisplayName("callback bem-sucedido: status muda para SUCESSO")
        void callbackSucesso_statusMudaParaSucesso() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", ContestationStatus.EM_ANDAMENTO);
            SqsResultMessage message = buildMessage("req-001", true);

            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));
            when(callbackClient.executeCallback(message)).thenReturn(true);
            when(requestRepository.save(any())).thenReturn(entity);

            // Act
            callbackService.processResult(message);

            // Assert
            assertThat(entity.getCommunicationStatus()).isEqualTo(ContestationStatus.SUCESSO);
            assertThat(entity.getLastError()).isNull();
            verify(requestRepository).save(entity);
            verify(contestationService).saveAuditLog(
                    eq("req-001"),
                    eq(ContestationStatus.EM_ANDAMENTO),
                    eq(ContestationStatus.SUCESSO),
                    anyString());
        }

        @Test
        @DisplayName("callback falhou: status muda para CALLBACK_FALHA com lastError")
        void callbackFalhou_statusMudaParaCallbackFalha() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", ContestationStatus.EM_ANDAMENTO);
            SqsResultMessage message = buildMessage("req-001", false);
            message = SqsResultMessage.builder()
                    .requestId("req-001")
                    .contestationId("CONT-001")
                    .correlationId("corr-001")
                    .success(false)
                    .resultDetails("Timeout no serviço externo")
                    .build();

            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));
            when(callbackClient.executeCallback(any())).thenReturn(false);
            when(requestRepository.save(any())).thenReturn(entity);

            // Act
            callbackService.processResult(message);

            // Assert
            assertThat(entity.getCommunicationStatus()).isEqualTo(ContestationStatus.CALLBACK_FALHA);
            assertThat(entity.getLastError()).isNotBlank();
            ArgumentCaptor<ContestationStatus> statusCaptor = ArgumentCaptor.forClass(ContestationStatus.class);
            verify(contestationService).saveAuditLog(
                    eq("req-001"),
                    eq(ContestationStatus.EM_ANDAMENTO),
                    statusCaptor.capture(),
                    anyString());
            assertThat(statusCaptor.getValue()).isEqualTo(ContestationStatus.CALLBACK_FALHA);
        }
    }

    @Nested
    @DisplayName("processResult - entidade não encontrada")
    class ProcessResultNaoEncontrada {

        @Test
        @DisplayName("requestId inexistente: ignora sem chamar callback")
        void requestIdInexistente_ignoraSemChamarCallback() {
            // Arrange
            when(requestRepository.findById("req-404")).thenReturn(Optional.empty());
            SqsResultMessage message = buildMessage("req-404", true);

            // Act
            callbackService.processResult(message);

            // Assert
            verify(callbackClient, never()).executeCallback(any());
            verify(requestRepository, never()).save(any());
            verify(contestationService, never()).saveAuditLog(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("processResult - status terminal ignorado")
    class ProcessResultStatusTerminal {

        @Test
        @DisplayName("já SUCESSO: ignora sem reprocessar")
        void jaEmSucesso_ignoraSemReprocessar() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", ContestationStatus.SUCESSO);
            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));
            SqsResultMessage message = buildMessage("req-001", true);

            // Act
            callbackService.processResult(message);

            // Assert
            verify(callbackClient, never()).executeCallback(any());
            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("já CANCELADO: ignora sem chamar callback")
        void jaCancelado_ignoraSemChamarCallback() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", ContestationStatus.CANCELADO);
            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));
            SqsResultMessage message = buildMessage("req-001", true);

            // Act
            callbackService.processResult(message);

            // Assert
            verify(callbackClient, never()).executeCallback(any());
            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("CALLBACK_FALHA: não é terminal, reprocessa normalmente")
        void callbackFalha_naoETerminal_reprocessa() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", ContestationStatus.CALLBACK_FALHA);
            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));
            when(callbackClient.executeCallback(any())).thenReturn(true);
            when(requestRepository.save(any())).thenReturn(entity);
            SqsResultMessage message = buildMessage("req-001", true);

            // Act
            callbackService.processResult(message);

            // Assert — deve processar e atualizar para SUCESSO
            verify(callbackClient).executeCallback(any());
            assertThat(entity.getCommunicationStatus()).isEqualTo(ContestationStatus.SUCESSO);
        }
    }

    private ContestationRequest buildEntity(String id, ContestationStatus status) {
        return ContestationRequest.builder()
                .id(id)
                .contestationId("CONT-001")
                .communicationType(ContestationType.CONTESTACAO_ABERTA)
                .communicationStatus(status)
                .payload("{\"amount\":100.0}")
                .correlationId("corr-test")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private SqsResultMessage buildMessage(String requestId, boolean success) {
        return SqsResultMessage.builder()
                .requestId(requestId)
                .contestationId("CONT-001")
                .correlationId("corr-001")
                .success(success)
                .resultDetails(success ? "Processado com sucesso" : "Falha no processamento")
                .build();
    }
}
