package com.gestormatic.backend.procedure.controller;

import com.gestormatic.backend.auth.security.SupabasePrincipal;
import com.gestormatic.backend.procedure.dto.MemberResponse;
import com.gestormatic.backend.procedure.dto.ProcedureResponse;
import com.gestormatic.backend.procedure.dto.RequiredDocumentResponse;
import com.gestormatic.backend.procedure.dto.StepResponse;
import com.gestormatic.backend.procedure.service.ProcedureService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcedureControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;

    @MockitoBean ProcedureService service;
    @MockitoBean JwtDecoder jwtDecoder;

    private static final String TENANT = "tenant-1";
    private static final String GESTOR_UID = "gestor-uid";
    private static final Long TEMPLATE_ID = 1L;
    private static final Long STEP_ID = 10L;

    private UsernamePasswordAuthenticationToken gestorAuth;
    private ProcedureResponse sampleResponse;
    private StepResponse sampleStep;

    @BeforeEach
    void setUp() {
        SupabasePrincipal principal = new SupabasePrincipal(GESTOR_UID, "g@g.com", "Gestor", TENANT, List.of("gestor"));
        gestorAuth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        sampleStep = new StepResponse(STEP_ID, 1, "Paso 1", "Descripción", List.of());
        sampleResponse = new ProcedureResponse(TEMPLATE_ID, "Trámite", "Desc", "DRAFT",
                GESTOR_UID, OffsetDateTime.now(), List.of(sampleStep));
    }

    // ─── POST /api/procedures ─────────────────────────────────────────────────

    @Nested
    class CreateProcedure {

        @Test
        void returns201_andBody_whenAuthenticated() throws Exception {
            when(service.createTemplate(any(), any())).thenReturn(sampleResponse);

            mockMvc.perform(post("/api/procedures")
                            .with(authentication(gestorAuth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Trámite\",\"description\":\"Desc\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(TEMPLATE_ID))
                    .andExpect(jsonPath("$.name").value("Trámite"))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        void returns401_whenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/procedures")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"X\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─── GET /api/procedures ──────────────────────────────────────────────────

    @Nested
    class ListProcedures {

        @Test
        void returns200_withList_whenAuthenticated() throws Exception {
            when(service.listTemplates(any())).thenReturn(List.of(sampleResponse));

            mockMvc.perform(get("/api/procedures")
                            .with(authentication(gestorAuth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(TEMPLATE_ID));
        }

        @Test
        void returns401_whenUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/procedures"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─── GET /api/procedures/{id} ─────────────────────────────────────────────

    @Nested
    class GetProcedure {

        @Test
        void returns200_withStepsAndDocuments_whenAuthenticated() throws Exception {
            when(service.getTemplate(any(), eq(TEMPLATE_ID))).thenReturn(sampleResponse);

            mockMvc.perform(get("/api/procedures/{id}", TEMPLATE_ID)
                            .with(authentication(gestorAuth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(TEMPLATE_ID))
                    .andExpect(jsonPath("$.steps.length()").value(1))
                    .andExpect(jsonPath("$.steps[0].name").value("Paso 1"));
        }
    }

    // ─── PUT /api/procedures/{id} ─────────────────────────────────────────────

    @Nested
    class UpdateProcedure {

        @Test
        void returns200_withUpdatedFields() throws Exception {
            ProcedureResponse updated = new ProcedureResponse(TEMPLATE_ID, "Nuevo", "Nueva desc",
                    "DRAFT", GESTOR_UID, OffsetDateTime.now(), List.of());
            when(service.updateTemplate(any(), eq(TEMPLATE_ID), any())).thenReturn(updated);

            mockMvc.perform(put("/api/procedures/{id}", TEMPLATE_ID)
                            .with(authentication(gestorAuth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Nuevo\",\"description\":\"Nueva desc\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Nuevo"));
        }
    }

    // ─── PATCH /api/procedures/{id}/status ───────────────────────────────────

    @Nested
    class ChangeStatus {

        @Test
        void returns200_withUpdatedStatus() throws Exception {
            ProcedureResponse active = new ProcedureResponse(TEMPLATE_ID, "Trámite", "Desc",
                    "ACTIVE", GESTOR_UID, OffsetDateTime.now(), List.of());
            when(service.changeTemplateStatus(any(), eq(TEMPLATE_ID), any())).thenReturn(active);

            mockMvc.perform(patch("/api/procedures/{id}/status", TEMPLATE_ID)
                            .with(authentication(gestorAuth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }
    }

    // ─── POST /api/procedures/{id}/steps ─────────────────────────────────────

    @Nested
    class AddStep {

        @Test
        void returns201_withStep() throws Exception {
            when(service.addStep(any(), eq(TEMPLATE_ID), any())).thenReturn(sampleStep);

            mockMvc.perform(post("/api/procedures/{id}/steps", TEMPLATE_ID)
                            .with(authentication(gestorAuth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"stepOrder\":1,\"name\":\"Paso 1\",\"description\":\"Desc\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(STEP_ID))
                    .andExpect(jsonPath("$.name").value("Paso 1"))
                    .andExpect(jsonPath("$.stepOrder").value(1));
        }
    }

    // ─── PUT /api/procedures/{id}/steps/{stepId} ──────────────────────────────

    @Nested
    class UpdateStep {

        @Test
        void returns200_withUpdatedStep() throws Exception {
            StepResponse updated = new StepResponse(STEP_ID, 1, "Actualizado", "Nueva desc", List.of());
            when(service.updateStep(any(), eq(TEMPLATE_ID), eq(STEP_ID), any())).thenReturn(updated);

            mockMvc.perform(put("/api/procedures/{id}/steps/{stepId}", TEMPLATE_ID, STEP_ID)
                            .with(authentication(gestorAuth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Actualizado\",\"description\":\"Nueva desc\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Actualizado"));
        }
    }

    // ─── POST /api/procedures/{id}/steps/{stepId}/documents ──────────────────

    @Nested
    class AddDocument {

        @Test
        void returns201_withDocument() throws Exception {
            RequiredDocumentResponse doc = new RequiredDocumentResponse(20L, "DNI", "Obligatorio", true);
            when(service.addRequiredDocument(any(), eq(TEMPLATE_ID), eq(STEP_ID), any())).thenReturn(doc);

            mockMvc.perform(post("/api/procedures/{id}/steps/{stepId}/documents", TEMPLATE_ID, STEP_ID)
                            .with(authentication(gestorAuth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"DNI\",\"description\":\"Obligatorio\",\"mandatory\":true}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("DNI"))
                    .andExpect(jsonPath("$.mandatory").value(true));
        }
    }

    // ─── GET /api/procedures/{id}/members ────────────────────────────────────

    @Nested
    class ListMembers {

        @Test
        void returns200_withMemberList() throws Exception {
            MemberResponse member = new MemberResponse(1L, GESTOR_UID, "OWNER");
            when(service.listMembers(any(), eq(TEMPLATE_ID))).thenReturn(List.of(member));

            mockMvc.perform(get("/api/procedures/{id}/members", TEMPLATE_ID)
                            .with(authentication(gestorAuth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].memberRole").value("OWNER"));
        }
    }

    // ─── POST /api/procedures/{id}/members ───────────────────────────────────

    @Nested
    class AddMember {

        @Test
        void returns201_withMember() throws Exception {
            MemberResponse member = new MemberResponse(2L, "customer-uid", "CUSTOMER");
            when(service.addMember(any(), eq(TEMPLATE_ID), any())).thenReturn(member);

            mockMvc.perform(post("/api/procedures/{id}/members", TEMPLATE_ID)
                            .with(authentication(gestorAuth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":\"customer-uid\",\"memberRole\":\"CUSTOMER\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.memberRole").value("CUSTOMER"));
        }
    }

    // ─── DELETE /api/procedures/{id}/members/{userId} ────────────────────────

    @Nested
    class RemoveMember {

        @Test
        void returns204_whenMemberRemoved() throws Exception {
            doNothing().when(service).removeMember(any(), eq(TEMPLATE_ID), eq("customer-uid"));

            mockMvc.perform(delete("/api/procedures/{id}/members/{userId}", TEMPLATE_ID, "customer-uid")
                            .with(authentication(gestorAuth)))
                    .andExpect(status().isNoContent());

            verify(service).removeMember(any(), eq(TEMPLATE_ID), eq("customer-uid"));
        }
    }
}
