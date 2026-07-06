package com.hermes.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test against the bundled landscape.json: 18 systems, 39
 * integrations, one intentional cycle. Read-only checks run first, the
 * mutating scenarios afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LandscapeApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    void healthMapExposesTheLoadedLandscape() throws Exception {
        mockMvc.perform(get("/landscape/health-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemCount").value(18))
                .andExpect(jsonPath("$.integrationCount").value(39));
    }

    @Test
    @Order(2)
    void warningsIncludeTheIntentionalCycleAndSingleSourceConsumers() throws Exception {
        MvcResult result = mockMvc.perform(get("/landscape/warnings"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode warnings = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(warnings).anySatisfy(w -> {
            assertThat(w.get("type").asText()).isEqualTo("CYCLE");
            assertThat(w.get("message").asText())
                    .contains("ARCHIVE_SYSTEM").contains("LEGACY_ERP").contains("LEGACY_WMS");
        });
        assertThat(warnings).anySatisfy(w -> {
            assertThat(w.get("type").asText()).isEqualTo("SINGLE_SOURCE_CONSUMER");
            assertThat(w.get("message").asText()).contains("BANK_GATEWAY");
        });
    }

    @Test
    @Order(3)
    void statisticsSummarizeTheGraph() throws Exception {
        mockMvc.perform(get("/landscape/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSystems").value(18))
                .andExpect(jsonPath("$.totalIntegrations").value(39))
                .andExpect(jsonPath("$.cycleCount").value(1))
                .andExpect(jsonPath("$.topCriticalSystems[0].id").value("SAP_ECC"));
    }

    @Test
    @Order(10)
    void sapEccOutageProducesAP1ReportWithCorrectClassifications() throws Exception {
        MvcResult result = mockMvc.perform(post("/analysis/impact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"system\":\"SAP_ECC\",\"degradationType\":\"COMPLETE_OUTAGE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("P1"))
                .andExpect(jsonPath("$.statistics.totalAffectedSystems").value(14))
                .andExpect(jsonPath("$.statistics.maxCascadeDepth").value(2))
                .andReturn();

        JsonNode report = objectMapper.readTree(result.getResponse().getContentAsString());

        JsonNode salesforce = findSystem(report, "SALESFORCE");
        assertThat(salesforce.get("impactType").asText()).isEqualTo("DIRECT");
        assertThat(salesforce.get("sourcingStatus").asText()).isEqualTo("ORPHANED");

        JsonNode finance = findSystem(report, "FINANCE_SYSTEM");
        assertThat(finance.get("sourcingStatus").asText()).isEqualTo("PARTIAL"); // HR + bank files still flow

        JsonNode portal = findSystem(report, "CUSTOMER_PORTAL");
        assertThat(portal.get("impactType").asText()).isEqualTo("CASCADE");

        // recovery starts at the failed system, cycle members get flagged
        JsonNode recovery = report.get("recommendedRecoverySequence");
        assertThat(recovery.get(0).get("systemId").asText()).isEqualTo("SAP_ECC");

        assertThat(report.get("landscapeWarnings")).anySatisfy(w ->
                assertThat(w.asText()).contains("Cycle detected"));

        // the report is retrievable by id afterwards
        String reportId = report.get("reportId").asText();
        mockMvc.perform(get("/analysis/impact/" + reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degradedSystemId").value("SAP_ECC"));

        // and the health map now reflects the incident
        mockMvc.perform(get("/landscape/health-map"))
                .andExpect(jsonPath("$.systems[?(@.id == 'SAP_ECC')].status").value("DOWN"));

        mockMvc.perform(post("/landscape/reset")).andExpect(status().isOk());
    }

    @Test
    @Order(11)
    void recoverySequenceFlagsCycleMembersForManualIntervention() throws Exception {
        MvcResult result = mockMvc.perform(post("/analysis/recovery-sequence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"system\":\"LEGACY_WMS\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode steps = objectMapper.readTree(result.getResponse().getContentAsString());
        // LEGACY_WMS sits on the WMS -> ARCHIVE -> LEGACY_ERP cycle, so the
        // failed system itself becomes the break point
        assertThat(steps.get(0).get("systemId").asText()).isEqualTo("LEGACY_WMS");
        assertThat(steps.get(0).get("manualInterventionRequired").asBoolean()).isTrue();
        assertThat(steps).hasSizeGreaterThan(5);
    }

    @Test
    @Order(20)
    void unknownSystemYields404ProblemDetail() throws Exception {
        mockMvc.perform(post("/analysis/impact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"system\":\"NOT_A_SYSTEM\",\"degradationType\":\"COMPLETE_OUTAGE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Unknown system 'NOT_A_SYSTEM'"));
    }

    @Test
    @Order(21)
    void invalidPayloadYields400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/landscape/systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"bad id!\",\"name\":\"\",\"businessCriticality\":42}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.businessCriticality").exists())
                .andExpect(jsonPath("$.fieldErrors.id").exists());
    }

    @Test
    @Order(22)
    void duplicateSystemRegistrationYields409() throws Exception {
        mockMvc.perform(post("/landscape/systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"SAP_ECC\",\"name\":\"SAP ECC 6.0\",\"type\":\"SAP_ERP\","
                                + "\"businessCriticality\":10}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(23)
    void registeringSystemAndIntegrationExtendsTheGraph() throws Exception {
        mockMvc.perform(post("/landscape/systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"TEST_CACHE\",\"name\":\"Distributed Cache\",\"type\":\"CACHE\","
                                + "\"businessCriticality\":4}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/landscape/integrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"IFL-900\",\"name\":\"Cache Warmup Feed\",\"source\":\"SAP_ECC\","
                                + "\"target\":\"TEST_CACHE\",\"protocol\":\"REST\",\"dataType\":\"Materials\","
                                + "\"businessProcess\":\"Order to Cash\",\"criticality\":3,\"slaClass\":\"BATCH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("IFL-900"));

        mockMvc.perform(get("/landscape/health-map"))
                .andExpect(jsonPath("$.systemCount").value(19))
                .andExpect(jsonPath("$.integrationCount").value(40));
    }

    private static JsonNode findSystem(JsonNode report, String systemId) {
        for (JsonNode node : report.get("affectedSystems")) {
            if (node.get("systemId").asText().equals(systemId)) {
                return node;
            }
        }
        throw new AssertionError(systemId + " not found in affected systems");
    }
}
