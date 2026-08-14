package com.aiphoto.repository;

import com.aiphoto.entity.FaceCluster;
import com.aiphoto.entity.Person;
import com.aiphoto.entity.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FaceClusterRepository extends JpaRepository<FaceCluster, Long> {

    List<FaceCluster> findByPhoto(Photo photo);

    List<FaceCluster> findByPerson(Person person);

    @Query(value = "SELECT * FROM face_clusters WHERE person_id IS NULL", nativeQuery = true)
    List<FaceCluster> findUnassigned();

    @Query(value = "SELECT * FROM face_clusters WHERE embedding <=> CAST(:query_vector AS vector) < :threshold ORDER BY embedding <=> CAST(:query_vector AS vector) LIMIT :limit", nativeQuery = true)
    List<FaceCluster> findByVectorSimilarity(@Param("query_vector") String queryVector, @Param("threshold") double threshold, @Param("limit") int limit);

    @Query(value = "SELECT person_id FROM face_clusters WHERE embedding <=> CAST(:query_vector AS vector) < :threshold ORDER BY embedding <=> CAST(:query_vector AS vector) LIMIT :limit", nativeQuery = true)
    List<Long> findPersonIdByVectorSimilarity(@Param("query_vector") String queryVector, @Param("threshold") double threshold, @Param("limit") int limit);
}
