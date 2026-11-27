package com.gucardev.dynamicreportsentity.platform.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

/** {@link FileStore} on an S3 bucket (AWS or any S3-compatible store). */
@Slf4j
class S3FileStore implements FileStore, AutoCloseable {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties.S3 settings;

    S3FileStore(StorageProperties.S3 settings) {
        this.settings = settings;
        AwsCredentialsProvider credentials = settings.accessKey() == null || settings.accessKey().isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(settings.accessKey(), settings.secretKey()));
        S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(settings.pathStyle()).build();
        // Checksums only where S3 requires them: otherwise the SDK uploads "aws-chunked" with checksum trailers, and
        // S3-compatible stores that keep that Content-Encoding (S3Mock, older Ceph) hand it back to browsers, which
        // then refuse or corrupt the download.
        var client = S3Client.builder().region(Region.of(settings.region())).credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        var signer = S3Presigner.builder().region(Region.of(settings.region())).credentialsProvider(credentials)
                .serviceConfiguration(pathStyle);
        if (settings.endpoint() != null) {
            client.endpointOverride(settings.endpoint());
            signer.endpointOverride(settings.publicEndpoint() != null ? settings.publicEndpoint() : settings.endpoint());
        }
        this.s3 = client.build();
        this.presigner = signer.build();
        if (settings.createBucket()) {
            createBucketIfMissing();
        }
    }

    private void createBucketIfMissing() {
        try {
            s3.headBucket(request -> request.bucket(settings.bucket()));
        } catch (NoSuchBucketException e) {
            s3.createBucket(request -> request.bucket(settings.bucket()));
            log.info("Created bucket {}", settings.bucket());
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            s3.createBucket(request -> request.bucket(settings.bucket()));
            log.info("Created bucket {}", settings.bucket());
        }
    }

    @Override
    public void close() {
        presigner.close();
        s3.close();
    }

    @Override
    public void put(String key, Path file, String contentType) {
        s3.putObject(request -> request.bucket(settings.bucket()).key(key).contentType(contentType),
                RequestBody.fromFile(file));
    }

    @Override
    public InputStream open(String key) {
        try {
            return s3.getObject(request -> request.bucket(settings.bucket()).key(key));
        } catch (NoSuchKeyException e) {
            throw new StoredFileMissingException(key, e);
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(request -> request.bucket(settings.bucket()).key(key));
    }

    @Override
    public Optional<URI> temporaryDownloadUrl(String key, String fileName, String contentType) {
        if (!settings.presignedDownloads()) {
            return Optional.empty();
        }
        String disposition = ContentDisposition.attachment().filename(fileName, UTF_8).build().toString();
        URI url = URI.create(presigner.presignGetObject(presign -> presign
                .signatureDuration(settings.presignedUrlTtl())
                .getObjectRequest(get -> get.bucket(settings.bucket()).key(key)
                        .responseContentDisposition(disposition).responseContentType(contentType)
                        // files uploaded before the setting above may carry "aws-chunked"; the body is plain
                        .responseContentEncoding("identity")))
                .url().toString());
        return Optional.of(url);
    }
}
