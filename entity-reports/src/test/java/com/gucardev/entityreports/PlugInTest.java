package com.gucardev.entityreports;

import com.gucardev.entityreports.Dataset.RowPolicy;
import com.gucardev.entityreports.Dataset.RowRestriction;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a new project does to get reporting: add the dependency, annotate its entities, done. No JPA here (the catalog
 * bean is given by hand), no Spring Security (the caller is the servlet principal), plain H2.
 */
@SpringBootTest(classes = PlugInTest.Host.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:plugin;DB_CLOSE_DELAY=-1",
        "entity-reports.saved.initialize-schema=true",
        "entity-reports.web.path=/reports"})
@AutoConfigureMockMvc
class PlugInTest {

    @DatasetEntity(label = "Region")
    static class Region {
        @Id Long id;
        @DatasetField(label = "Name") String name;
    }

    @DatasetEntity(label = "Store", includeAllFields = true)
    static class Store {
        @Id Long id;
        String name;
        BigDecimal revenue;
        @DatasetReference(target = "Region", inverse = "stores", owner = true) Long regionId;
        @DatasetHidden String secret;
    }

    @SpringBootApplication
    static class Host {

        @Bean
        DatasetCatalog catalog() {
            return DatasetCatalog.of(List.of(Region.class, Store.class));
        }

        /** Only region 1 is visible; its stores follow because Store declares Region as its owner. */
        @Bean
        RowPolicy onlyRegionOne() {
            return viewer -> List.of(new RowRestriction("Region", "id", 1L));
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    void data(JdbcTemplate jdbc) {
        jdbc.execute("create table if not exists region (id bigint primary key, name varchar(50))");
        jdbc.execute("create table if not exists store (id bigint primary key, name varchar(50), revenue decimal(10,2),"
                + " region_id bigint, secret varchar(50))");
        jdbc.execute("merge into region key (id) values (1, 'North'), (2, 'South')");
        jdbc.execute("merge into store key (id) values (1, 'A', 100, 1, 'x'), (2, 'B', 50, 1, 'y'), (3, 'C', 70, 2, 'z')");
    }

    @Test
    void catalogRunExportAndSaveWithoutAnyHostCode() {
        assertThat(mvc.get().uri("/reports/catalog").exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.entities[?(@.name == 'Store')].fields[*].name").asArray()
                .containsExactly("id", "name", "revenue");

        String revenuePerRegion = """
                {"root": "Store",
                 "columns": [{"field": "region.name"}, {"field": "revenue", "aggregate": "SUM"}],
                 "sort": [{"field": "region.name"}]}""";
        assertThat(mvc.post().uri("/reports/run").contentType(MediaType.APPLICATION_JSON).content(revenuePerRegion)
                .exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.rows").asArray().singleElement().asString().contains("North", "150");

        assertThat(mvc.post().uri("/reports/export?format=xlsx").contentType(MediaType.APPLICATION_JSON)
                .content(revenuePerRegion).exchange()).hasStatusOk().hasContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        assertThat(mvc.post().uri("/reports/run").contentType(MediaType.APPLICATION_JSON)
                .content("{\"root\": \"Store\", \"columns\": [{\"field\": \"secret\"}]}").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code")
                .isEqualTo("INVALID_DATASET_DEFINITION");

        assertThat(mvc.post().uri("/api/saved-datasets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"revenue_per_region\", \"definition\": %s}".formatted(revenuePerRegion))
                .exchange()).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.data.name").isEqualTo("revenue_per_region");
    }
}
