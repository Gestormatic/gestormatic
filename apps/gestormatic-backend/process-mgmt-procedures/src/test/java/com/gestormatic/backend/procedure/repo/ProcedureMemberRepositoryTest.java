package com.gestormatic.backend.procedure.repo;

import com.gestormatic.backend.procedure.model.ProcedureMember;
import com.gestormatic.backend.procedure.model.ProcedureTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test")
class ProcedureMemberRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JwtDecoder jwtDecoder;

    @Autowired ProcedureMemberRepository memberRepo;
    @Autowired ProcedureTemplateRepository templateRepo;

    private Long templateId;
    private Long otherTemplateId;

    @BeforeEach
    void setUp() {
        memberRepo.deleteAll();
        templateRepo.deleteAll();
        templateId = saveTemplate("Trámite A").getId();
        otherTemplateId = saveTemplate("Trámite B").getId();
    }

    @Test
    void findAllByTemplateId_returnsOnlyMembersOfTemplate() {
        saveMember(templateId, "owner-uid", "OWNER");
        saveMember(templateId, "customer-uid", "CUSTOMER");
        saveMember(otherTemplateId, "other-uid", "OWNER");

        List<ProcedureMember> result = memberRepo.findAllByTemplateId(templateId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProcedureMember::getUserId)
                .containsExactlyInAnyOrder("owner-uid", "customer-uid");
    }

    @Test
    void findAllByTemplateId_returnsEmptyList_whenNoMembers() {
        List<ProcedureMember> result = memberRepo.findAllByTemplateId(templateId);

        assertThat(result).isEmpty();
    }

    @Test
    void findByTemplateIdAndUserId_returnsPresent_whenExists() {
        saveMember(templateId, "gestor-uid", "OWNER");

        Optional<ProcedureMember> result = memberRepo.findByTemplateIdAndUserId(templateId, "gestor-uid");

        assertThat(result).isPresent();
        assertThat(result.get().getMemberRole()).isEqualTo("OWNER");
    }

    @Test
    void findByTemplateIdAndUserId_returnsEmpty_whenUserNotInTemplate() {
        saveMember(templateId, "gestor-uid", "OWNER");

        Optional<ProcedureMember> result = memberRepo.findByTemplateIdAndUserId(templateId, "unknown-uid");

        assertThat(result).isEmpty();
    }

    @Test
    void findByTemplateIdAndUserId_returnsEmpty_whenWrongTemplate() {
        saveMember(templateId, "gestor-uid", "OWNER");

        Optional<ProcedureMember> result = memberRepo.findByTemplateIdAndUserId(otherTemplateId, "gestor-uid");

        assertThat(result).isEmpty();
    }

    @Test
    void deleteByTemplateIdAndUserId_removesMember() {
        saveMember(templateId, "to-delete", "CUSTOMER");
        saveMember(templateId, "to-keep", "OWNER");

        memberRepo.deleteByTemplateIdAndUserId(templateId, "to-delete");

        List<ProcedureMember> remaining = memberRepo.findAllByTemplateId(templateId);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getUserId()).isEqualTo("to-keep");
    }

    @Test
    void deleteByTemplateIdAndUserId_doesNothing_whenMemberDoesNotExist() {
        saveMember(templateId, "existing-uid", "OWNER");

        memberRepo.deleteByTemplateIdAndUserId(templateId, "nonexistent-uid");

        assertThat(memberRepo.findAllByTemplateId(templateId)).hasSize(1);
    }

    @Test
    void deleteByTemplateIdAndUserId_doesNotDeleteMemberFromOtherTemplate() {
        saveMember(otherTemplateId, "shared-uid", "CUSTOMER");
        saveMember(templateId, "shared-uid", "CUSTOMER");

        memberRepo.deleteByTemplateIdAndUserId(templateId, "shared-uid");

        assertThat(memberRepo.findByTemplateIdAndUserId(otherTemplateId, "shared-uid")).isPresent();
        assertThat(memberRepo.findByTemplateIdAndUserId(templateId, "shared-uid")).isEmpty();
    }

    private ProcedureTemplate saveTemplate(String name) {
        ProcedureTemplate t = new ProcedureTemplate();
        t.setTenantId("tenant-1");
        t.setName(name);
        t.setStatus("DRAFT");
        t.setCreatedBy("uid");
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        return templateRepo.save(t);
    }

    private ProcedureMember saveMember(Long tId, String userId, String role) {
        ProcedureMember m = new ProcedureMember();
        m.setTemplateId(tId);
        m.setUserId(userId);
        m.setMemberRole(role);
        return memberRepo.save(m);
    }
}
