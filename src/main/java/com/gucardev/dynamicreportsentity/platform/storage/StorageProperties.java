package com.gucardev.dynamicreportsentity.platform.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/**
 * {@code storage.*}.
 *
 * @param type {@code s3} (default) or {@code filesystem}
 */
@ConfigurationProperties("storage")
public record StorageProperties(@DefaultValue("s3") String type, @DefaultValue S3 s3,
                                @DefaultValue FileSystem filesystem) {

    /**
     * @param endpoint           S3-compatible endpoint; empty = AWS for {@code region}
     * @param publicEndpoint     endpoint browsers reach, used in presigned URLs when it differs from {@code endpoint}
     *                           (e.g. an in-cluster service vs. an OpenShift route); empty = {@code endpoint}
     * @param accessKey          empty = the default AWS credential chain (env, web identity on OpenShift/EKS, ...)
     * @param pathStyle          {@code bucket} in the path instead of the host name; most non-AWS stores need it
     * @param createBucket       create the bucket at startup if missing (development)
     * @param presignedDownloads downloads redirect to a presigned URL; false = streamed through the app (when the
     *                           store is not reachable from browsers)
     * @param presignedUrlTtl    lifetime of presigned URLs
     */
    public record S3(URI endpoint, URI publicEndpoint, @DefaultValue("us-east-1") String region,
                     @DefaultValue("flow-files") String bucket, String accessKey, String secretKey,
                     @DefaultValue("true") boolean pathStyle, @DefaultValue("false") boolean createBucket,
                     @DefaultValue("true") boolean presignedDownloads,
                     @DefaultValue("5m") Duration presignedUrlTtl) {
    }

    /** @param directory root directory of the files; must be shared by all instances */
    public record FileSystem(@DefaultValue("files") Path directory) {
    }
}
