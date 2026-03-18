package com.gestormatic.backend.procedure.dto;

import java.time.OffsetDateTime;

public record CaseResponse(
        Long id,
        String tenantId,
        Long templateId,
        String templateName,
        String customerId,
        String assignedTo,
        String status,
        String notes,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
