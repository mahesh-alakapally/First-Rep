package com.firstrep.storage;

import com.firstrep.config.AppProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.core.sync.RequestBody;

@Configuration
public class StorageConfig {
    @Bean
    @ConditionalOnProperty(name = "firstrep.storage.mode", havingValue = "memory")
    ObjectStorage memoryStorage() {
        return new MemoryStorage();
    }

    @Bean
    @ConditionalOnProperty(name = "firstrep.storage.mode", havingValue = "s3", matchIfMissing = true)
    ObjectStorage s3Storage(AppProperties properties) {
        var storage = properties.getStorage();
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(storage.getAccessKey(), storage.getSecretKey()));
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(storage.getEndpoint()))
                .region(Region.of(storage.getRegion()))
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create(storage.getEndpoint()))
                .region(Region.of(storage.getRegion()))
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        ensureBucket(client, storage.getBucket());
        return new S3Storage(client, presigner, storage.getBucket());
    }

    private static void ensureBucket(S3Client client, String bucket) {
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
                return;
            } catch (NoSuchBucketException ex) {
                client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                return;
            } catch (Exception ex) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    static final class MemoryStorage implements ObjectStorage {
        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        private final Map<String, String> types = new ConcurrentHashMap<>();

        @Override
        public void put(String key, byte[] body, String contentType) {
            objects.put(key, body);
            types.put(key, contentType);
        }

        @Override
        public byte[] get(String key) {
            byte[] body = objects.get(key);
            if (body == null) {
                throw new IllegalStateException("Missing object");
            }
            return body;
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }

        @Override
        public String presignPut(String key, String contentType, Duration ttl) {
            return "memory://" + key;
        }
    }

    static final class S3Storage implements ObjectStorage {
        private final S3Client client;
        private final S3Presigner presigner;
        private final String bucket;

        S3Storage(S3Client client, S3Presigner presigner, String bucket) {
            this.client = client;
            this.presigner = presigner;
            this.bucket = bucket;
        }

        @Override
        public void put(String key, byte[] body, String contentType) {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(body));
        }

        @Override
        public byte[] get(String key) {
            return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
        }

        @Override
        public void delete(String key) {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        }

        @Override
        public String presignPut(String key, String contentType, Duration ttl) {
            var presign = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .putObjectRequest(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build())
                    .build());
            return presign.url().toString();
        }
    }
}
