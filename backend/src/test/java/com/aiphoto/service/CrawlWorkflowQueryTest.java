package com.aiphoto.service;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.entity.CrawlAssetSource;
import com.aiphoto.entity.CrawlJob;
import com.aiphoto.entity.CrawlPage;
import com.aiphoto.entity.CrawlScanPage;
import com.aiphoto.repository.CrawlAssetRepository;
import com.aiphoto.repository.CrawlJobRepository;
import com.aiphoto.repository.CrawlPageRepository;
import com.aiphoto.repository.CrawlScanPageRepository;
import java.util.List;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class CrawlWorkflowQueryTest {
    @Test void repositoryQueriesResolveAgainstTheEntityMappings() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try (var factory = new MetadataSources(registry)
                .addAnnotatedClass(CrawlJob.class).addAnnotatedClass(CrawlPage.class)
                .addAnnotatedClass(CrawlAsset.class).addAnnotatedClass(CrawlAssetSource.class)
                .addAnnotatedClass(CrawlScanPage.class)
                .buildMetadata().buildSessionFactory(); var session = factory.openSession()) {
            for (var repository : List.of(CrawlPageRepository.class, CrawlScanPageRepository.class,
                    CrawlJobRepository.class, CrawlAssetRepository.class)) {
                for (var method : repository.getDeclaredMethods()) {
                    Query query = method.getAnnotation(Query.class);
                    if (query != null && !query.nativeQuery()) session.createQuery(query.value());
                }
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
