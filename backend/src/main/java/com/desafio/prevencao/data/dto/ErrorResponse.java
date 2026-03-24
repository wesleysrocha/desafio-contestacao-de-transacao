package com.desafio.prevencao.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "Resposta de erro padronizada")
public class ErrorResponse {

    @Schema(description = "Código HTTP do erro")
    private int status;

    @Schema(description = "Mensagem principal do erro")
    private String message;

    @Schema(description = "Detalhes adicionais de validação")
    private List<String> details;

    @Schema(description = "Correlation ID para rastreamento")
    private String correlationId;

    @Schema(description = "Timestamp do erro")
    private LocalDateTime timestamp;
}
