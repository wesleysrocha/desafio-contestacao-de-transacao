package com.desafio.prevencao.domain.entity;

import com.desafio.prevencao.domain.enums.ContestationStatus;
import com.desafio.prevencao.domain.enums.ContestationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "contestation_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_contestation_id", columnNames = "contestation_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContestationRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "contestation_id", nullable = false, length = 255)
    private String contestationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "communication_type", nullable = false, length = 50)
    private ContestationType communicationType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "communication_status", nullable = false, length = 50)
    private ContestationStatus communicationStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
