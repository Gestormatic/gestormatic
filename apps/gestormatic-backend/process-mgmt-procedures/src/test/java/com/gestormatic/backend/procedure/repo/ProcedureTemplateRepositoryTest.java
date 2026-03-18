package com.gestormatic.backend.procedure.repo;

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
class ProcedureTemplateRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JwtDecoder jwtDecoder;

    @Autowired
    ProcedureTemplateRepository repo;

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @BeforeEach
    void cleanUp() {
        repo.deleteAll();
    }

    @Test
    void findAllByTenantId_returnsOnlyMatchingTenant() {
        save("Trámite A1", TENANT_A);
        save("Trámite A2", TENANT_A);
        save("Trámite B1", TENANT_B);

        List<ProcedureTemplate> result = repo.findAllByTenantId(TENANT_A);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProcedureTemplate::getName)
                .containsExactlyInAnyOrder("Trámite A1", "Trámite A2");
    }

    @Test
    void findAllByTenantId_returnsOrderedByCreatedAtDesc() throws InterruptedException {
        ProcedureTemplate first = save("Primero", TENANT_A);
        Thread.sleep(10);
        ProcedureTemplate second = save("Segundo", TENANT_A);

        List<ProcedureTemplate> result = repo.findAllByTenantId(TENANT_A);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Segundo");
        assertThat(result.get(1).getName()).isEqualTo("Primero");
    }

    @Test
    void findAllByTenantId_returnsEmptyList_whenNoTemplatesForTenant() {
        save("Trámite B1", TENANT_B);

        List<ProcedureTemplate> result = repo.findAllByTenantId(TENANT_A);

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndTenantId_returnsTemplate_whenMatchingIdAndTenant() {
        ProcedureTemplate saved = save("Trámite A", TENANT_A);

        Optional<ProcedureTemplate> result = repo.findByIdAndTenantId(saved.getId(), TENANT_A);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Trámite A");
    }

    @Test
    void findByIdAndTenantId_returnsEmpty_whenWrongTenant() {
        ProcedureTemplate saved = save("Trámite A", TENANT_A);

        Optional<ProcedureTemplate> result = repo.findByIdAndTenantId(saved.getId(), TENANT_B);

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndTenantId_returnsEmpty_whenIdDoesNotExist() {
        Optional<ProcedureTemplate> result = repo.findByIdAndTenantId(9999L, TENANT_A);

        assertThat(result).isEmpty();
    }

    private ProcedureTemplate save(String name, String tenantId) {
        ProcedureTemplate t = new ProcedureTemplate();
        t.setTenantId(tenantId);
        t.setName(name);
        t.setStatus("DRAFT");
        t.setCreatedBy("uid");
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        return repo.save(t);
    }
}
