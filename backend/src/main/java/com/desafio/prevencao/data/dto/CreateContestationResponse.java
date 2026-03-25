package com.desafio.prevencao.data.dto;

import com.desafio.prevencao.domain.enums.ContestationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Resposta ao criar uma contestação (202 Accepted)")
public class CreateContestationResponse {

    @Schema(description = "ID único da solicitação para acompanhamento", example = "550e8400-e29b-41d4-a716-446655440000")
    private String requestId;

    @Schema(description = "Status inicial da solicitação")
    private ContestationStatus status;

    @Schema(description = "Data/hora em que a solicitação foi recebida")
    private LocalDateTime receivedAt;

    @Schema(description = "Correlation ID para rastreamento distribuído")
    private String correlationId;

    @Schema(description = "Indica se foi idempotente (evento já existia)", example = "false")
    private boolean idempotent;
}
