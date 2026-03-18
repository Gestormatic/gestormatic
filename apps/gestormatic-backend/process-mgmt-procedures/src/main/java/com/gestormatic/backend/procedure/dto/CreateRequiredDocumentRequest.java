package com.gestormatic.backend.procedure.dto;

public record CreateRequiredDocumentRequest(String name, String description, boolean mandatory) {
}
