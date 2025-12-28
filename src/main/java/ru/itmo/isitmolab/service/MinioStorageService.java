package ru.itmo.isitmolab.service;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import ru.itmo.isitmolab.exception.StorageUnavailableException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

@ApplicationScoped
public class MinioStorageService {

    private MinioClient client;

    private String endpoint;
    private String bucket;

    // ленивый флаг
    private volatile boolean bucketReady = false;
    private final Object bucketLock = new Object();

    @PostConstruct
    public void init() {
        // system props из скрипта: -Dminio.url, -Dminio.accessKey, -Dminio.secretKey, -Dstorage.importsBucket
        // env fallback: MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_BUCKET
        this.endpoint = firstNonBlank(
                System.getProperty("minio.url"),
                System.getenv("MINIO_ENDPOINT"),
                "http://localhost:9000"
        );

        String accessKey = firstNonBlank(
                System.getProperty("minio.accessKey"),
                System.getenv("MINIO_ACCESS_KEY"),
                "minioadmin"
        );

        String secretKey = firstNonBlank(
                System.getProperty("minio.secretKey"),
                System.getenv("MINIO_SECRET_KEY"),
                "minioadmin"
        );

        this.bucket = firstNonBlank(
                System.getProperty("storage.importsBucket"),
                System.getenv("MINIO_BUCKET"),
                "imports"
        );

        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        System.out.println("MinIO endpoint=" + endpoint + " bucket=" + bucket);
    }

    private static String firstNonBlank(String a, String b, String def) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return def;
    }

    private String get(String key, String def) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) v = System.getProperty(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    /**
     * Ленивая проверка/создание bucket с retry.
     */
    private void ensureBucket() {
        if (bucketReady) return;

        synchronized (bucketLock) {
            if (bucketReady) return;

            RuntimeException last = null;
            int attempts = 3;

            for (int i = 1; i <= attempts; i++) {
                try {
                    boolean exists = client.bucketExists(
                            BucketExistsArgs.builder().bucket(bucket).build()
                    );
                    if (!exists) {
                        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    }
                    bucketReady = true;
                    return;
                } catch (Exception e) {
                    last = new RuntimeException(e);
                    // маленькая пауза, чтобы не молотить MinIO
                    try {
                        Thread.sleep(200L * i);
                    } catch (InterruptedException ignored) {
                    }
                }
            }

            throw new StorageUnavailableException("MinIO ensureBucket failed for bucket=" + bucket + " endpoint=" + endpoint,
                    last == null ? null : last.getCause());
        }
    }

    public void putObject(String objectKey, byte[] bytes, String contentType, Map<String, String> userMeta) {
        ensureBucket();
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            PutObjectArgs.Builder b = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(in, bytes.length, -1)
                    .contentType(contentType);

            if (userMeta != null && !userMeta.isEmpty()) b.userMetadata(userMeta);

            client.putObject(b.build());
        } catch (Exception e) {
            throw new StorageUnavailableException("MinIO putObject failed: key=" + objectKey, e);
        }
    }

    public InputStream getObject(String objectKey) {
        ensureBucket();
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new StorageUnavailableException("MinIO getObject failed: key=" + objectKey, e);
        }
    }

    public void statObject(String objectKey) {
        ensureBucket();
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new StorageUnavailableException("MinIO statObject failed: key=" + objectKey, e);
        }
    }

    public void copyObject(String fromKey, String toKey) {
        ensureBucket();
        try {
            client.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucket)
                            .object(toKey)
                            .source(CopySource.builder().bucket(bucket).object(fromKey).build())
                            .build()
            );
        } catch (Exception e) {
            throw new StorageUnavailableException("MinIO copyObject failed: " + fromKey + " -> " + toKey, e);
        }
    }

    public void removeObjectQuietly(String objectKey) {
        try {
            ensureBucket();
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ignored) {
            // best-effort
        }
    }
}