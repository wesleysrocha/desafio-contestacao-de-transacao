package com.desafio.prevencao.data.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
@Schema(description = "Payload para criação de uma nova contestação")
public class CreateContestationRequest {

    @NotBlank(message = "contestationId é obrigatório")
    @Schema(description = "Identificador da contestação no sistema de origem", example = "CONT-123456")
    private String contestationId;

    @NotNull(message = "amount é obrigatório")
    @DecimalMin(value = "0.01", message = "amount deve ser maior que 0")
    @Schema(description = "Valor da contestação (deve ser maior que 0)", example = "1500.00")
    private BigDecimal amount;

    private Map<String, Object> additionalData = new HashMap<>();

    @JsonAnySetter
    public void setAdditionalData(String key, Object value) {
        if (!"amount".equals(key) && !"contestationId".equals(key)) {
            this.additionalData.put(key, value);
        }
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }
}
