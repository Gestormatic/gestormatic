package com.gestormatic.backend.procedure.controller;

import com.gestormatic.backend.auth.security.SupabasePrincipal;
import com.gestormatic.backend.procedure.dto.CaseResponse;
import com.gestormatic.backend.procedure.service.CaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaseControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;

    @MockitoBean CaseService service;
    @MockitoBean JwtDecoder jwtDecoder;

    private static final String TENANT = "tenant-1";
    private static final String GESTOR_UID = "gestor-uid";
    private static final Long CASE_ID = 10L;
    private static final Long TEMPLATE_ID = 1L;

    private UsernamePasswordAuthenticationToken gestorAuth;
    private CaseResponse sampleResponse;

    @BeforeEach
    void setUp() {
        SupabasePrincipal principal = new SupabasePrincipal(GESTOR_UID, "g@g.com", "Gestor", TENANT, List.of("gestor"));
        gestorAuth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        sampleResponse = new CaseResponse(CASE_ID, TENANT, TEMPLATE_ID, "Trámite Test",
                "customer-uid", null, "RECEIVED", null, GESTOR_UID, OffsetDateTime.now(), OffsetDateTime.now());
    }

    // ─── POST /api/cases ─────────────────────────────────────────────────────

    @Nested
    class CreateCase {

        @Test
        void returns201_andBody_whenAuthenticated() throws Exception {
            when(service.createCase(any(), any())).thenReturn(sampleResponse);

            mockMvc.perform(post("/api/cases")
                            .with(authentication(gestorAuth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"templateId\":1,\"customerId\":\"customer-uid\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(CASE_ID))
                    .andExpect(jsonPath("$.status").value("RECEIVED"))
                    .andExpect(jsonPath("$.templateName").value("Trámite Test"));
        }

        @Test
        void returns401_whenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/cases")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"templateId\":1,\"customerId\":\"cust\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─── GET /api/cases ──────────────────────────────────────────────────────

    @Nested
    class ListCases {

        @Test
        void returns200_withList_whenAuthenticated() throws Exception {
            when(service.listCases(any(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of(sampleResponse));

            mockMvc.perform(get("/api/cases")
                            .with(authentication(gestorAuth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(CASE_ID));
        }

        @Test
        void returns200_withStatusFilter() throws Exception {
            when(service.listCases(any(), eq("RECEIVED"), isNull(), isNull(), isNull()))
                    .thenReturn(List.of(sampleResponse));

            mockMvc.perform(get("/api/cases")
                            .param("status", "RECEIVED")
                            .with(authentication(gestorAuth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        void returns401_whenUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/cases"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─── GET /api/cases/{id} ─────────────────────────────────────────────────

    @Nested
    class GetCase {

        @Test
        void returns200_withCase_whenAuthenticated() throws Exception {
            when(service.getCase(any(), eq(CASE_ID))).thenReturn(sampleResponse);

            mockMvc.perform(get("/api/cases/{id}", CASE_ID)
                            .with(authentication(gestorAuth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(CASE_ID))
                    .andExpect(jsonPath("$.customerId").value("customer-uid"));
        }

        @Test
        void returns401_whenUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/cases/{id}", CASE_ID))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─── PATCH /api/cases/{id}/status ────────────────────────────────────────

    @Nested
    class ChangeCaseStatus {

        @Test
        void returns200_withUpdatedStatus() throws Exception {
            CaseResponse inReview = new CaseResponse(CASE_ID, TENANT, TEMPLATE_ID, "Trámite Test",
                    "customer-uid", null, "IN_REVIEW", null, GESTOR_UID, OffsetDateTime.now(), OffsetDateTime.now());
            when(service.changeCaseStatus(any(), eq(CASE_ID), any())).thenReturn(inReview);

            mockMvc.perform(patch("/api/cases/{id}/status", CASE_ID)
                            .with(authentication(gestorAuth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"IN_REVIEW\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("IN_REVIEW"));
        }

        @Test
        void returns401_whenUnauthenticated() throws Exception {
            mockMvc.perform(patch("/api/cases/{id}/status", CASE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"IN_REVIEW\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─── PATCH /api/cases/{id}/assignee ──────────────────────────────────────

    @Nested
    class AssignCase {

        @Test
        void returns200_withAssignedTo() throws Exception {
            CaseResponse assigned = new CaseResponse(CASE_ID, TENANT, TEMPLATE_ID, "Trámite Test",
                    "customer-uid", "gestor-2", "IN_REVIEW", null, GESTOR_UID, OffsetDateTime.now(), OffsetDateTime.now());
            when(service.assignCase(any(), eq(CASE_ID), any())).thenReturn(assigned);

            mockMvc.perform(patch("/api/cases/{id}/assignee", CASE_ID)
                            .with(authentication(gestorAuth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"assignedTo\":\"gestor-2\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.assignedTo").value("gestor-2"));
        }

        @Test
        void returns401_whenUnauthenticated() throws Exception {
            mockMvc.perform(patch("/api/cases/{id}/assignee", CASE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"assignedTo\":\"uid\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
