package com.desafio.prevencao.service;

import com.desafio.prevencao.data.dto.*;
import com.desafio.prevencao.data.mapper.ContestationMapper;
import com.desafio.prevencao.domain.entity.ContestationAuditLog;
import com.desafio.prevencao.domain.entity.ContestationRequest;
import com.desafio.prevencao.domain.enums.ContestationStatus;
import com.desafio.prevencao.domain.enums.ContestationType;
import com.desafio.prevencao.middleware.exception.BusinessException;
import com.desafio.prevencao.middleware.exception.ConflictException;
import com.desafio.prevencao.middleware.exception.ResourceNotFoundException;
import com.desafio.prevencao.repositories.ContestationAuditLogRepository;
import com.desafio.prevencao.repositories.ContestationRequestRepository;
import com.desafio.prevencao.resourses.sqs.ContestationRequestProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContestationService - Testes Unitários")
class ContestationServiceTest {

    @Mock
    private ContestationRequestRepository requestRepository;
    @Mock
    private ContestationAuditLogRepository auditLogRepository;
    @Mock
    private ContestationRequestProducer producer;
    @Mock
    private ContestationMapper mapper;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private ContestationService service;

    @BeforeEach
    void setUp() {
        service = new ContestationService(
                requestRepository, auditLogRepository, producer, mapper, objectMapper);
    }

    // =========================================================================
    // createContestation
    // =========================================================================
    @Nested
    @DisplayName("createContestation")
    class CreateContestation {

        @Test
        @DisplayName("sucesso: persiste, audita e enfileira na SQS")
        void sucesso_persisteAuditaEnfileira() {
            // Arrange
            CreateContestationRequest request = buildRequest("CONT-001", new BigDecimal("150.00"));
            ContestationRequest saved = buildEntity("req-uuid", "CONT-001", ContestationStatus.EM_ANDAMENTO);

            when(requestRepository.existsByContestationId("CONT-001")).thenReturn(false);
            when(requestRepository.save(any())).thenReturn(saved);

            // Act
            CreateContestationResponse response = service.createContestation(request);

            // Assert
            assertThat(response.getRequestId()).isEqualTo("req-uuid");
            assertThat(response.getStatus()).isEqualTo(ContestationStatus.EM_ANDAMENTO);
            assertThat(response.isIdempotent()).isFalse();
            assertThat(response.getCorrelationId()).isNotBlank();

            verify(requestRepository).save(any(ContestationRequest.class));
            verify(auditLogRepository).save(any(ContestationAuditLog.class));
            verify(producer).send(any(SqsContestationMessage.class));
        }

        @Test
        @DisplayName("sucesso: payload inclui amount serializado corretamente")
        void sucesso_payloadContemAmount() {
            // Arrange
            CreateContestationRequest request = buildRequest("CONT-AMT", new BigDecimal("1500.50"));
            ArgumentCaptor<ContestationRequest> captor = ArgumentCaptor.forClass(ContestationRequest.class);

            when(requestRepository.existsByContestationId(any())).thenReturn(false);
            when(requestRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            // Act
            service.createContestation(request);

            // Assert
            String payload = captor.getValue().getPayload();
            assertThat(payload).contains("1500.5");
        }

        @Test
        @DisplayName("sucesso: mensagem SQS contém contestationId e correlationId")
        void sucesso_mensagemSqsCorreta() {
            // Arrange
            CreateContestationRequest request = buildRequest("CONT-SQS", new BigDecimal("200.00"));
            ContestationRequest saved = buildEntity("req-sqs", "CONT-SQS", ContestationStatus.EM_ANDAMENTO);
            ArgumentCaptor<SqsContestationMessage> sqsCaptor =
                    ArgumentCaptor.forClass(SqsContestationMessage.class);

            when(requestRepository.existsByContestationId(any())).thenReturn(false);
            when(requestRepository.save(any())).thenReturn(saved);

            // Act
            service.createContestation(request);

            // Assert
            verify(producer).send(sqsCaptor.capture());
            SqsContestationMessage msg = sqsCaptor.getValue();
            assertThat(msg.getContestationId()).isEqualTo("CONT-SQS");
            assertThat(msg.getRequestId()).isEqualTo("req-sqs");
            assertThat(msg.getCommunicationType()).isEqualTo(ContestationType.CONTESTACAO_ABERTA);
        }

        @Test
        @DisplayName("conflito: contestationId duplicado lança ConflictException")
        void conflito_contestationIdDuplicado_lancaConflictException() {
            // Arrange
            CreateContestationRequest request = buildRequest("CONT-DUP", new BigDecimal("100.00"));
            when(requestRepository.existsByContestationId("CONT-DUP")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> service.createContestation(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("CONT-DUP");

            verify(requestRepository, never()).save(any());
            verify(producer, never()).send(any());
            verify(auditLogRepository, never()).save(any());
        }
    }

    // =========================================================================
    // findById
    // =========================================================================
    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("encontrado: retorna response com histórico de auditoria")
        void encontrado_retornaResponseComHistorico() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", "CONT-001", ContestationStatus.EM_ANDAMENTO);
            List<ContestationAuditLog> logs = List.of(buildAuditLog("req-001"));
            ContestationResponse expected = buildResponse("CONT-001", ContestationStatus.EM_ANDAMENTO);

            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));
            when(auditLogRepository.findByRequestIdOrderByCreatedAtAsc("req-001")).thenReturn(logs);
            when(mapper.toResponse(entity, logs)).thenReturn(expected);

            // Act
            ContestationResponse result = service.findById("req-001");

            // Assert
            assertThat(result).isEqualTo(expected);
            verify(auditLogRepository).findByRequestIdOrderByCreatedAtAsc("req-001");
        }

        @Test
        @DisplayName("não encontrado: lança ResourceNotFoundException")
        void naoEncontrado_lancaResourceNotFoundException() {
            when(requestRepository.findById("req-404")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById("req-404"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // listContestations
    // =========================================================================
    @Nested
    @DisplayName("listContestations")
    class ListContestations {

        @Test
        @DisplayName("sem filtros: retorna página com resultado")
        void semFiltros_retornaPagina() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", "CONT-001", ContestationStatus.EM_ANDAMENTO);
            ContestationResponse responseDto = buildResponse("CONT-001", ContestationStatus.EM_ANDAMENTO);
            Page<ContestationRequest> page = new PageImpl<>(List.of(entity));

            when(requestRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
            when(mapper.toResponse(entity)).thenReturn(responseDto);

            // Act
            Page<ContestationResponse> result = service.listContestations(0, 10, null, null, null, null);

            // Assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getContestationId()).isEqualTo("CONT-001");
        }

        @Test
        @DisplayName("com filtros: delega specification ao repository")
        void comFiltros_delegaSpecification() {
            // Arrange
            Page<ContestationRequest> empty = new PageImpl<>(List.of());
            when(requestRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(empty);

            // Act
            Page<ContestationResponse> result = service.listContestations(
                    0, 10,
                    ContestationStatus.SUCESSO, "CONT-XYZ",
                    LocalDateTime.now().minusDays(1), LocalDateTime.now());

            // Assert
            assertThat(result.getTotalElements()).isEqualTo(0);
            verify(requestRepository).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("lista vazia: retorna página vazia sem erro")
        void listaVazia_retornaPaginaVazia() {
            // Arrange
            Page<ContestationRequest> empty = new PageImpl<>(List.of());
            when(requestRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(empty);

            // Act
            Page<ContestationResponse> result = service.listContestations(0, 10, null, null, null, null);

            // Assert
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    // =========================================================================
    // cancelContestation
    // =========================================================================
    @Nested
    @DisplayName("cancelContestation")
    class CancelContestation {

        @Test
        @DisplayName("sucesso: EM_ANDAMENTO → CANCELADO, registra auditoria")
        void sucesso_emAndamentoParaCancelado() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", "CONT-001", ContestationStatus.EM_ANDAMENTO);
            ContestationResponse expected = buildResponse("CONT-001", ContestationStatus.CANCELADO);

            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));
            when(requestRepository.save(any())).thenReturn(entity);
            when(mapper.toResponse(entity)).thenReturn(expected);

            // Act
            ContestationResponse result = service.cancelContestation("req-001");

            // Assert
            assertThat(entity.getCommunicationStatus()).isEqualTo(ContestationStatus.CANCELADO);
            assertThat(result.getCommunicationStatus()).isEqualTo(ContestationStatus.CANCELADO);
            verify(requestRepository).save(entity);
            verify(auditLogRepository).save(any(ContestationAuditLog.class));
        }

        @Test
        @DisplayName("idempotente: já CANCELADO não salva novamente")
        void idempotente_jaCancelado_naoSalvaNovamente() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", "CONT-001", ContestationStatus.CANCELADO);
            ContestationResponse expected = buildResponse("CONT-001", ContestationStatus.CANCELADO);

            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));
            when(mapper.toResponse(entity)).thenReturn(expected);

            // Act
            ContestationResponse result = service.cancelContestation("req-001");

            // Assert
            assertThat(result.getCommunicationStatus()).isEqualTo(ContestationStatus.CANCELADO);
            verify(requestRepository, never()).save(any());
            verify(auditLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("status SUCESSO: lança BusinessException")
        void statusSucesso_lancaBusinessException() {
            ContestationRequest entity = buildEntity("req-001", "CONT-001", ContestationStatus.SUCESSO);
            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.cancelContestation("req-001"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("SUCESSO");
        }

        @Test
        @DisplayName("status CALLBACK_FALHA: lança BusinessException")
        void statusCallbackFalha_lancaBusinessException() {
            ContestationRequest entity = buildEntity("req-001", "CONT-001", ContestationStatus.CALLBACK_FALHA);
            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.cancelContestation("req-001"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("não encontrado: lança ResourceNotFoundException")
        void naoEncontrado_lancaResourceNotFoundException() {
            when(requestRepository.findById("req-404")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelContestation("req-404"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // replayContestation
    // =========================================================================
    @Nested
    @DisplayName("replayContestation")
    class ReplayContestation {

        @Test
        @DisplayName("sucesso: define EM_ANDAMENTO, limpa lastError e enfileira na SQS")
        void sucesso_defineEmAndamentoEnfileira() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", "CONT-001", ContestationStatus.CALLBACK_FALHA);
            entity.setLastError("Erro anterior");

            when(requestRepository.findById("req-001")).thenReturn(Optional.of(entity));
            when(requestRepository.save(any())).thenReturn(entity);

            // Act
            service.replayContestation("req-001");

            // Assert
            assertThat(entity.getCommunicationStatus()).isEqualTo(ContestationStatus.EM_ANDAMENTO);
            assertThat(entity.getLastError()).isNull();
            verify(producer).send(any(SqsContestationMessage.class));
            verify(auditLogRepository).save(any(ContestationAuditLog.class));
        }

        @Test
        @DisplayName("não encontrado: lança ResourceNotFoundException")
        void naoEncontrado_lancaResourceNotFoundException() {
            when(requestRepository.findById("req-404")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.replayContestation("req-404"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private CreateContestationRequest buildRequest(String contestationId, BigDecimal amount) {
        CreateContestationRequest req = new CreateContestationRequest();
        req.setContestationId(contestationId);
        req.setAmount(amount);
        return req;
    }

    private ContestationRequest buildEntity(String id, String contestationId, ContestationStatus status) {
        return ContestationRequest.builder()
                .id(id)
                .contestationId(contestationId)
                .communicationType(ContestationType.CONTESTACAO_ABERTA)
                .communicationStatus(status)
                .payload("{\"amount\":100.0}")
                .correlationId("corr-test")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private ContestationResponse buildResponse(String contestationId, ContestationStatus status) {
        return ContestationResponse.builder()
                .requestId("req-001")
                .contestationId(contestationId)
                .communicationType(ContestationType.CONTESTACAO_ABERTA)
                .communicationStatus(status)
                .auditHistory(List.of())
                .build();
    }

    private ContestationAuditLog buildAuditLog(String requestId) {
        return ContestationAuditLog.builder()
                .id("audit-001")
                .requestId(requestId)
                .toStatus(ContestationStatus.EM_ANDAMENTO)
                .message("Teste")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
