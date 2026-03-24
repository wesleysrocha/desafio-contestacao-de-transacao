package com.desafio.prevencao.data.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SqsResultMessage {

    private String requestId;
    private String contestationId;
    private String correlationId;
    private boolean success;
    private String resultDetails;
}
