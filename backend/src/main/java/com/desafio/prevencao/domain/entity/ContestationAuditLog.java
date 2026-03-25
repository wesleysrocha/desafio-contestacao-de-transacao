package com.desafio.prevencao.domain.entity;

import com.desafio.prevencao.domain.enums.ContestationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "contestation_audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContestationAuditLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 50)
    private ContestationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 50)
    private ContestationStatus toStatus;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        this.createdAt = LocalDateTime.now();
    }
}
