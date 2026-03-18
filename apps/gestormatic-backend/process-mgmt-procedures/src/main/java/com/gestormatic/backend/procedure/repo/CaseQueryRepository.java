package com.gestormatic.backend.procedure.repo;

import com.gestormatic.backend.procedure.dto.CaseResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
public class CaseQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CaseQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CaseResponse> findByFilters(String tenantId,
                                            String status,
                                            String customerId,
                                            String assignedTo,
                                            Long templateId) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.tenant_id, c.template_id, t.name AS template_name,
                       c.customer_id, c.assigned_to, c.status, c.notes,
                       c.created_by, c.created_at, c.updated_at
                FROM procedure_cases c
                JOIN procedure_templates t ON t.id = c.template_id
                WHERE c.tenant_id = :tenantId
                """);

        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenantId);
        List<String> conditions = new ArrayList<>();

        if (status != null) {
            conditions.add("c.status = :status");
            params.addValue("status", status.toUpperCase());
        }
        if (customerId != null) {
            conditions.add("c.customer_id = :customerId");
            params.addValue("customerId", customerId);
        }
        if (assignedTo != null) {
            conditions.add("c.assigned_to = :assignedTo");
            params.addValue("assignedTo", assignedTo);
        }
        if (templateId != null) {
            conditions.add("c.template_id = :templateId");
            params.addValue("templateId", templateId);
        }

        for (String condition : conditions) {
            sql.append("AND ").append(condition).append("\n");
        }
        sql.append("ORDER BY c.created_at DESC");

        return jdbc.query(sql.toString(), params, (rs, rowNum) -> mapRow(rs));
    }

    private CaseResponse mapRow(ResultSet rs) throws SQLException {
        return new CaseResponse(
                rs.getLong("id"),
                rs.getString("tenant_id"),
                rs.getLong("template_id"),
                rs.getString("template_name"),
                rs.getString("customer_id"),
                rs.getString("assigned_to"),
                rs.getString("status"),
                rs.getString("notes"),
                rs.getString("created_by"),
                toOffsetDateTime(rs.getTimestamp("created_at")),
                toOffsetDateTime(rs.getTimestamp("updated_at"))
        );
    }

    private OffsetDateTime toOffsetDateTime(Timestamp ts) {
        if (ts == null) return null;
        return ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
