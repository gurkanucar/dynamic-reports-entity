package com.gucardev.dynamicreportsentity.platform.dataset.catalog;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Discovers dataset-ready entities through the JPA metamodel, so the datasets module never has to
 * know (or import) the business modules that own them.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
class CatalogConfiguration {

    @Bean
    DatasetCatalog datasetCatalog(EntityManagerFactory entityManagerFactory) {
        List<Class<?>> entityTypes = entityManagerFactory.getMetamodel().getEntities().stream()
                .<Class<?>>map(EntityType::getJavaType)
                .toList();
        DatasetCatalog catalog = DatasetCatalog.of(entityTypes);
        log.info("Dataset catalog ready: {} entities, {} navigations", catalog.entities().size(),
                catalog.entities().stream().mapToInt(entity -> entity.navigations().size()).sum());
        return catalog;
    }
}
