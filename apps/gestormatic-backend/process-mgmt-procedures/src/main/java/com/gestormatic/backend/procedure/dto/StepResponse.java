package com.gestormatic.backend.procedure.dto;

import java.util.List;

public record StepResponse(Long id, Integer stepOrder, String name, String description,
                           List<RequiredDocumentResponse> requiredDocuments) {
}
