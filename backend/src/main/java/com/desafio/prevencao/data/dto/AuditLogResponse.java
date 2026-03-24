package com.desafio.prevencao.data.dto;

import com.desafio.prevencao.domain.enums.ContestationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Entrada de auditoria de transição de status")
public class AuditLogResponse {

    @Schema(description = "ID do log de auditoria")
    private String id;

    @Schema(description = "Status anterior")
    private ContestationStatus fromStatus;

    @Schema(description = "Novo status")
    private ContestationStatus toStatus;

    @Schema(description = "Mensagem descritiva da transição")
    private String message;

    @Schema(description = "Data/hora da transição")
    private LocalDateTime createdAt;
}
