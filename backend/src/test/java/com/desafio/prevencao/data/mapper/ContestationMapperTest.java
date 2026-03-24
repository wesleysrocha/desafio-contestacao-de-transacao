package com.desafio.prevencao.data.mapper;

import com.desafio.prevencao.data.dto.AuditLogResponse;
import com.desafio.prevencao.data.dto.ContestationResponse;
import com.desafio.prevencao.domain.entity.ContestationAuditLog;
import com.desafio.prevencao.domain.entity.ContestationRequest;
import com.desafio.prevencao.domain.enums.ContestationStatus;
import com.desafio.prevencao.domain.enums.ContestationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContestationMapper - Testes Unitários")
class ContestationMapperTest {

    private ContestationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ContestationMapper();
    }


    @Nested
    @DisplayName("toResponse(entity) - sem auditoria")
    class ToResponseSemAuditoria {

        @Test
        @DisplayName("mapeia todos os campos da entidade corretamente")
        void mapeiaAllCamposCorretamente() {
            // Arrange
            LocalDateTime now = LocalDateTime.now();
            ContestationRequest entity = ContestationRequest.builder()
                    .id("req-001")
                    .contestationId("CONT-001")
                    .communicationType(ContestationType.CONTESTACAO_ABERTA)
                    .communicationStatus(ContestationStatus.EM_ANDAMENTO)
                    .payload("{\"amount\":200.0}")
                    .lastError(null)
                    .correlationId("corr-abc")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            // Act
            ContestationResponse result = mapper.toResponse(entity);

            // Assert
            assertThat(result.getRequestId()).isEqualTo("req-001");
            assertThat(result.getContestationId()).isEqualTo("CONT-001");
            assertThat(result.getCommunicationType()).isEqualTo(ContestationType.CONTESTACAO_ABERTA);
            assertThat(result.getCommunicationStatus()).isEqualTo(ContestationStatus.EM_ANDAMENTO);
            assertThat(result.getPayload()).isEqualTo("{\"amount\":200.0}");
            assertThat(result.getLastError()).isNull();
            assertThat(result.getCorrelationId()).isEqualTo("corr-abc");
            assertThat(result.getReceivedAt()).isEqualTo(now);
            assertThat(result.getUpdatedAt()).isEqualTo(now);
            assertThat(result.getAuditHistory()).isEmpty();
        }

        @Test
        @DisplayName("lastError preenchido é mapeado corretamente")
        void lastErrorPreenchido_mapeadoCorretamente() {
            // Arrange
            ContestationRequest entity = buildEntity("req-002", ContestationStatus.CALLBACK_FALHA);
            entity.setLastError("Timeout no serviço externo");

            // Act
            ContestationResponse result = mapper.toResponse(entity);

            // Assert
            assertThat(result.getLastError()).isEqualTo("Timeout no serviço externo");
            assertThat(result.getCommunicationStatus()).isEqualTo(ContestationStatus.CALLBACK_FALHA);
        }

        @Test
        @DisplayName("auditHistory é lista vazia quando não há logs")
        void semLogs_auditHistoryVazio() {
            // Arrange
            ContestationRequest entity = buildEntity("req-003", ContestationStatus.EM_ANDAMENTO);

            // Act
            ContestationResponse result = mapper.toResponse(entity);

            // Assert
            assertThat(result.getAuditHistory()).isNotNull();
            assertThat(result.getAuditHistory()).isEmpty();
        }
    }

    @Nested
    @DisplayName("toResponse(entity, logs) - com auditoria")
    class ToResponseComAuditoria {

        @Test
        @DisplayName("mapeia lista de auditoria com todos os campos")
        void mapeiaListaAuditoriaCompleta() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", ContestationStatus.SUCESSO);
            ContestationAuditLog log1 = buildAuditLog("audit-001", "req-001",
                    null, ContestationStatus.EM_ANDAMENTO, "Contestação criada");
            ContestationAuditLog log2 = buildAuditLog("audit-002", "req-001",
                    ContestationStatus.EM_ANDAMENTO, ContestationStatus.SUCESSO, "Processado com sucesso");

            // Act
            ContestationResponse result = mapper.toResponse(entity, List.of(log1, log2));

            // Assert
            assertThat(result.getAuditHistory()).hasSize(2);

            AuditLogResponse first = result.getAuditHistory().get(0);
            assertThat(first.getId()).isEqualTo("audit-001");
            assertThat(first.getFromStatus()).isNull();
            assertThat(first.getToStatus()).isEqualTo(ContestationStatus.EM_ANDAMENTO);
            assertThat(first.getMessage()).isEqualTo("Contestação criada");

            AuditLogResponse second = result.getAuditHistory().get(1);
            assertThat(second.getId()).isEqualTo("audit-002");
            assertThat(second.getFromStatus()).isEqualTo(ContestationStatus.EM_ANDAMENTO);
            assertThat(second.getToStatus()).isEqualTo(ContestationStatus.SUCESSO);
            assertThat(second.getMessage()).isEqualTo("Processado com sucesso");
        }

        @Test
        @DisplayName("lista de logs vazia resulta em auditHistory vazio")
        void listaVazia_auditHistoryVazio() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", ContestationStatus.EM_ANDAMENTO);

            // Act
            ContestationResponse result = mapper.toResponse(entity, Collections.emptyList());

            // Assert
            assertThat(result.getAuditHistory()).isEmpty();
        }

        @Test
        @DisplayName("lista de logs null resulta em auditHistory vazio")
        void listaNull_auditHistoryVazio() {
            // Arrange
            ContestationRequest entity = buildEntity("req-001", ContestationStatus.EM_ANDAMENTO);

            // Act
            ContestationResponse result = mapper.toResponse(entity, null);

            // Assert
            assertThat(result.getAuditHistory()).isNotNull();
            assertThat(result.getAuditHistory()).isEmpty();
        }
    }

    @Nested
    @DisplayName("toAuditLogResponse - mapeamento de log individual")
    class ToAuditLogResponse {

        @Test
        @DisplayName("mapeia campos do log de auditoria corretamente")
        void mapeiaLogAuditoriaCorretamente() {
            // Arrange
            LocalDateTime now = LocalDateTime.now();
            ContestationAuditLog log = ContestationAuditLog.builder()
                    .id("audit-abc")
                    .requestId("req-001")
                    .fromStatus(ContestationStatus.EM_ANDAMENTO)
                    .toStatus(ContestationStatus.CANCELADO)
                    .message("Cancelado pelo usuário")
                    .createdAt(now)
                    .build();

            // Act
            AuditLogResponse result = mapper.toAuditLogResponse(log);

            // Assert
            assertThat(result.getId()).isEqualTo("audit-abc");
            assertThat(result.getFromStatus()).isEqualTo(ContestationStatus.EM_ANDAMENTO);
            assertThat(result.getToStatus()).isEqualTo(ContestationStatus.CANCELADO);
            assertThat(result.getMessage()).isEqualTo("Cancelado pelo usuário");
            assertThat(result.getCreatedAt()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("toAuditLogResponses - lista de logs")
    class ToAuditLogResponses {

        @Test
        @DisplayName("lista com múltiplos logs é mapeada integralmente")
        void listaMúltiplosLogs_mapeadaIntegralmente() {
            // Arrange
            List<ContestationAuditLog> logs = List.of(
                    buildAuditLog("a1", "req-001", null, ContestationStatus.EM_ANDAMENTO, "Criado"),
                    buildAuditLog("a2", "req-001", ContestationStatus.EM_ANDAMENTO, ContestationStatus.SUCESSO, "Sucesso")
            );

            // Act
            List<AuditLogResponse> result = mapper.toAuditLogResponses(logs);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo("a1");
            assertThat(result.get(1).getId()).isEqualTo("a2");
        }

        @Test
        @DisplayName("lista null retorna lista vazia")
        void listaNull_retornaListaVazia() {
            List<AuditLogResponse> result = mapper.toAuditLogResponses(null);
            assertThat(result).isNotNull().isEmpty();
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

    private ContestationAuditLog buildAuditLog(String id, String requestId,
                                                ContestationStatus from, ContestationStatus to,
                                                String message) {
        return ContestationAuditLog.builder()
                .id(id)
                .requestId(requestId)
                .fromStatus(from)
                .toStatus(to)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
