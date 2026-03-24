package com.desafio.prevencao.data.dto;

import com.desafio.prevencao.domain.enums.ContestationStatus;
import com.desafio.prevencao.domain.enums.ContestationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resposta com dados de uma contestação")
public class ContestationResponse {

    @Schema(description = "ID único da solicitação (requestId)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String requestId;

    @Schema(description = "ID da contestação no sistema de origem", example = "CONT-123456")
    private String contestationId;

    @Schema(description = "Tipo da comunicação (sempre CONTESTACAO_ABERTA)")
    private ContestationType communicationType;

    @Schema(description = "Status atual da contestação")
    private ContestationStatus communicationStatus;

    @Schema(description = "Payload original enviado")
    private String payload;

    @Schema(description = "Último erro registrado (se houver)")
    private String lastError;

    @Schema(description = "Correlation ID para rastreamento")
    private String correlationId;

    @Schema(description = "Data/hora de criação da solicitação")
    private LocalDateTime receivedAt;

    @Schema(description = "Data/hora da última atualização")
    private LocalDateTime updatedAt;

    @Schema(description = "Histórico de auditoria (transitions de status)")
    private List<AuditLogResponse> auditHistory;
}
