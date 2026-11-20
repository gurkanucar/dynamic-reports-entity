package com.gucardev.dynamicreportsentity.platform.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "s3", matchIfMissing = true)
    FileStore s3FileStore(StorageProperties properties) {
        return new S3FileStore(properties.s3());
    }

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "filesystem")
    FileStore fileSystemFileStore(StorageProperties properties) {
        return new FileSystemFileStore(properties.filesystem().directory());
    }
}
