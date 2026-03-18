package com.gestormatic.backend.procedure.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ProcedureResponse(Long id, String name, String description, String status,
                                String createdBy, OffsetDateTime createdAt,
                                List<StepResponse> steps) {
}
