package com.desafio.prevencao.data.dto;

import com.desafio.prevencao.domain.enums.ContestationType;
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
public class SqsContestationMessage {

    private String requestId;
    private String contestationId;
    private ContestationType communicationType;
    private String payload;
    private String correlationId;
}
