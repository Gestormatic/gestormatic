package com.gestormatic.backend.procedure.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("required_documents")
public class RequiredDocument {

    @Id
    private Long id;

    @Column("step_id")
    private Long stepId;

    private String name;

    private String description;

    @Column("is_mandatory")
    private boolean mandatory;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStepId() { return stepId; }
    public void setStepId(Long stepId) { this.stepId = stepId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
}
