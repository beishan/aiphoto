package com.memoryvault.storage;

import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MinioStorageService {

    private final MinioClient minioClient;
    private final MinioClient publicMinioClient;

    @Value("${app.minio.bucket-photos}")
    private String bucketPhotos;

    @Value("${app.minio.bucket-thumbs}")
    private String bucketThumbs;

    public MinioStorageService(MinioClient minioClient, @Qualifier("publicMinioClient") MinioClient publicMinioClient) {
        this.minioClient = minioClient;
        this.publicMinioClient = publicMinioClient;
    }

    @PostConstruct
    public void init() {
        try {
            createBucketIfNotExists(bucketPhotos);
            createBucketIfNotExists(bucketThumbs);
        } catch (Exception e) {
            log.error("Failed to initialize MinIO buckets", e);
        }
    }

    public String uploadPhoto(byte[] data, String objectName, String contentType) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketPhotos)
                .object(objectName)
                .stream(new ByteArrayInputStream(data), data.length, -1)
                .contentType(contentType)
                .build());
        return objectName;
    }

    public String uploadThumbnail(byte[] data, String objectName) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketThumbs)
                .object(objectName)
                .stream(new ByteArrayInputStream(data), data.length, -1)
                .contentType("image/webp")
                .build());
        return objectName;
    }

    public InputStream downloadPhoto(String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketPhotos)
                .object(objectName)
                .build());
    }

    public String getPresignedUrl(String bucket, String objectName) throws Exception {
        // Use nginx proxy path for browser access
        return "/media/" + bucket + "/" + objectName;
    }

    public String getPhotoUrl(String objectName) throws Exception {
        return getPresignedUrl(bucketPhotos, objectName);
    }

    public String getThumbnailUrl(String objectName) throws Exception {
        return getPresignedUrl(bucketThumbs, objectName);
    }

    public void deleteObject(String bucket, String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
    }

    private void createBucketIfNotExists(String bucketName) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("Created MinIO bucket: {}", bucketName);
        }
        // Set bucket policy to allow public read access
        String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + bucketName + "/*\"]}]}";
        minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build());
    }
}
