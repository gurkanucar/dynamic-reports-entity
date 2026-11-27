package com.gucardev.entityreports;

import com.gucardev.entityreports.Dataset.RowPolicy;
import com.gucardev.entityreports.DatasetQueryBuilder.SqlDialect;
import com.gucardev.entityreports.DatasetWeb.ViewerResolver;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;

/**
 * Wires entity-reports into a Spring Boot app that has a {@link DataSource}: add the dependency (or copy the sources
 * into a package of yours: then component scanning finds this class), annotate entities with {@link DatasetEntity} /
 * {@link DatasetField} / {@link DatasetReference}, done. Every bean backs off when the
 * host defines its own ({@code @ConditionalOnMissingBean}); {@link RowPolicy} and {@link Clock} beans of the host are picked up.
 */
// A plain @Configuration (not @AutoConfiguration): Spring Boot keeps @AutoConfiguration classes out of component
// scanning, and copied sources are found by scanning. As a jar it is still an auto-configuration (listed in
// META-INF/spring/...AutoConfiguration.imports) and @AutoConfigureAfter orders it.
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(name = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"})
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "entity-reports", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(EntityReportsProperties.class)
public class EntityReportsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EntityReportsAutoConfiguration.class);

    /** The annotated entities among the JPA entities. Without JPA, define a bean with {@link DatasetCatalog#of}. */
    @Bean
    @ConditionalOnMissingBean
    DatasetCatalog datasetCatalog(ObjectProvider<EntityManagerFactory> entityManagerFactory) {
        EntityManagerFactory factory = entityManagerFactory.getIfAvailable();
        List<Class<?>> types = factory == null ? List.of()
                : factory.getMetamodel().getEntities().stream().<Class<?>>map(EntityType::getJavaType).toList();
        DatasetCatalog catalog = DatasetCatalog.of(types);
        log.info("entity-reports catalog ready: {} entities, {} relations", catalog.entities().size(),
                catalog.entities().stream().mapToInt(entity -> entity.navigations().size()).sum());
        return catalog;
    }

    @Bean
    @ConditionalOnMissingBean
    DatasetQueryBuilder datasetQueryBuilder(DatasetCatalog catalog, ObjectProvider<Clock> clock,
                                            ObjectProvider<RowPolicy> rowPolicies, DataSource dataSource)
            throws SQLException {
        SqlDialect dialect;
        try (Connection connection = dataSource.getConnection()) {
            dialect = SqlDialect.fromProductName(connection.getMetaData().getDatabaseProductName());
        }
        return new DatasetQueryBuilder(catalog, clock.getIfAvailable(Clock::systemDefaultZone),
                rowPolicies.orderedStream().toList(), dialect);
    }

    @Bean
    @ConditionalOnMissingBean
    IndexAdvisor datasetIndexAdvisor(DatasetCatalog catalog, DataSource dataSource) {
        return new IndexAdvisor(catalog, dataSource);
    }

    @Bean
    @ConditionalOnProperty(prefix = "entity-reports", name = "index-check-on-startup", matchIfMissing = true)
    ApplicationRunner datasetIndexCheck(IndexAdvisor advisor) {
        return args -> advisor.checkOnStartup();
    }

    @Bean
    @ConditionalOnMissingBean
    DatasetExecutor datasetExecutor(DatasetQueryBuilder queryBuilder, DataSource dataSource,
                                    ObjectProvider<JdbcTemplate> jdbcTemplate, EntityReportsProperties properties,
                                    PlatformTransactionManager transactionManager, IndexAdvisor indexAdvisor) {
        return new DatasetExecutor(queryBuilder, jdbcTemplate.getIfAvailable(() -> new JdbcTemplate(dataSource)),
                properties, transactionManager, indexAdvisor);
    }

    // ==== REST endpoints ==================================================================================

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(RestController.class)
    @ConditionalOnProperty(prefix = "entity-reports.web", name = "enabled", matchIfMissing = true)
    static class Web {

        // @ConditionalOnMissingBean: a host whose component scan covers this package already has them

        @Bean
        @ConditionalOnMissingBean
        DatasetWeb.DatasetController datasetController(DatasetCatalog catalog, DatasetExecutor executor,
                                                       IndexAdvisor indexAdvisor, ViewerResolver viewers,
                                                       EntityReportsProperties properties) {
            return new DatasetWeb.DatasetController(catalog, executor, indexAdvisor, viewers, properties);
        }

        @Bean
        @ConditionalOnMissingBean
        DatasetWeb.Errors datasetErrors() {
            return new DatasetWeb.Errors();
        }

        /** The servlet principal; with Spring Security its authorities too. Define your own to change it. */
        @Bean
        @ConditionalOnMissingBean
        ViewerResolver datasetViewerResolver() {
            return ViewerResolver.principal();
        }
    }
}
