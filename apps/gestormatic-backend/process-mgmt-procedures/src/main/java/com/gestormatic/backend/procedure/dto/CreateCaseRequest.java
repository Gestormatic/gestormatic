package com.gestormatic.backend.procedure.dto;

public record CreateCaseRequest(
        Long templateId,
        String customerId,
        String notes
) {}
