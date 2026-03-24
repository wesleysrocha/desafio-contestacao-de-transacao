package com.desafio.prevencao.service;

import com.desafio.prevencao.data.dto.SqsContestationMessage;
import com.desafio.prevencao.domain.entity.ContestationRequest;
import com.desafio.prevencao.domain.enums.ContestationStatus;
import com.desafio.prevencao.domain.enums.ContestationType;
import com.desafio.prevencao.repositories.ContestationRequestRepository;
import com.desafio.prevencao.resourses.client.CommDispatcherClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContestationProcessingService - Testes Unitários")
class ContestationProcessingServiceTest {

    @Mock
    private ContestationRequestRepository requestRepository;
    @Mock
    private CommDispatcherClient commDispatcherClient;
    @Mock
    private ContestationService contestationService;

    @InjectMocks
    private ContestationProcessingService processingService;

    // =========================================================================
    // processContestationRequest — sucesso
    // =========================================================================
    @Nested
    @DisplayName("processContestationRequest - sucesso")
    class ProcessSucesso {

        @Test
        @DisplayName("sucesso: chama dispatcher e não atualiza status")
        void sucesso_chamaDispatcher() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", ContestationStatus.EM_ANDAMENTO);
            SqsContestationMessage message = buildMessage("req-001");

            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));
            doNothing().when(commDispatcherClient).dispatch(any());

            // Act
            processingService.processContestationRequest(message);

            // Assert
            verify(commDispatcherClient).dispatch(message);
            verify(requestRepository, never()).save(any());
            verify(contestationService, never()).saveAuditLog(any(), any(), any(), any());
        }
    }

    // =========================================================================
    // processContestationRequest — entidade não encontrada
    // =========================================================================
    @Nested
    @DisplayName("processContestationRequest - entidade não encontrada")
    class ProcessNaoEncontrada {

        @Test
        @DisplayName("requestId inexistente: ignora sem chamar dispatcher")
        void requestIdInexistente_ignoraSemChamarDispatcher() {
            // Arrange
            when(requestRepository.findById("req-404")).thenReturn(Optional.empty());
            SqsContestationMessage message = buildMessage("req-404");

            // Act
            processingService.processContestationRequest(message);

            // Assert
            verify(commDispatcherClient, never()).dispatch(any());
            verify(requestRepository, never()).save(any());
        }
    }

    // =========================================================================
    // processContestationRequest — contestação cancelada
    // =========================================================================
    @Nested
    @DisplayName("processContestationRequest - contestação cancelada")
    class ProcessCancelada {

        @Test
        @DisplayName("já CANCELADO: ignora sem chamar dispatcher")
        void jaCancelado_ignoraSemChamarDispatcher() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", ContestationStatus.CANCELADO);
            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));
            SqsContestationMessage message = buildMessage("req-001");

            // Act
            processingService.processContestationRequest(message);

            // Assert
            verify(commDispatcherClient, never()).dispatch(any());
            verify(requestRepository, never()).save(any());
        }
    }

    // =========================================================================
    // processContestationRequest — erro no dispatcher
    // =========================================================================
    @Nested
    @DisplayName("processContestationRequest - erro no dispatcher")
    class ProcessErroDispatcher {

        @Test
        @DisplayName("exceção no dispatcher: persiste lastError e re-lança RuntimeException")
        void erroDispatcher_persisteLastErrorERelanca() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", ContestationStatus.EM_ANDAMENTO);
            SqsContestationMessage message = buildMessage("req-001");

            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));
            doThrow(new RuntimeException("Timeout na chamada HTTP"))
                    .when(commDispatcherClient).dispatch(any());
            when(requestRepository.save(any())).thenReturn(entity);

            // Act & Assert
            assertThatThrownBy(() -> processingService.processContestationRequest(message))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Falha ao processar contestação");

            // Verifica que lastError foi definido
            assertThat(entity.getLastError()).isNotBlank();
            assertThat(entity.getLastError()).contains("Timeout na chamada HTTP");

            // Verifica que o status permanece EM_ANDAMENTO (não muda)
            assertThat(entity.getCommunicationStatus()).isEqualTo(ContestationStatus.EM_ANDAMENTO);

            verify(requestRepository).save(entity);
            verify(contestationService).saveAuditLog(
                    eq("req-001"),
                    eq(ContestationStatus.EM_ANDAMENTO),
                    eq(ContestationStatus.EM_ANDAMENTO),
                    anyString());
        }

        @Test
        @DisplayName("exceção no dispatcher: não muda status da entidade para erro")
        void erroDispatcher_naoMudaStatusParaErro() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", ContestationStatus.EM_ANDAMENTO);
            SqsContestationMessage message = buildMessage("req-001");

            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));
            doThrow(new RuntimeException("Serviço indisponível"))
                    .when(commDispatcherClient).dispatch(any());
            when(requestRepository.save(any())).thenReturn(entity);

            // Act
            try {
                processingService.processContestationRequest(message);
            } catch (RuntimeException ignored) {
                // esperado
            }

            // Assert — status permanece EM_ANDAMENTO (não muda para CALLBACK_FALHA aqui)
            assertThat(entity.getCommunicationStatus()).isEqualTo(ContestationStatus.EM_ANDAMENTO);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================
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

    private SqsContestationMessage buildMessage(String requestId) {
        return SqsContestationMessage.builder()
                .requestId(requestId)
                .contestationId("CONT-001")
                .communicationType(ContestationType.CONTESTACAO_ABERTA)
                .payload("{\"amount\":100.0}")
                .correlationId("corr-test")
                .build();
    }
}
