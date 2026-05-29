package com.memoryvault.repository;

import com.memoryvault.entity.FaceCluster;
import com.memoryvault.entity.Person;
import com.memoryvault.entity.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FaceClusterRepository extends JpaRepository<FaceCluster, Long> {

    List<FaceCluster> findByPhoto(Photo photo);

    List<FaceCluster> findByPerson(Person person);

    @Query(value = "SELECT * FROM face_clusters WHERE person_id IS NULL", nativeQuery = true)
    List<FaceCluster> findUnassigned();

    @Query(value = "SELECT * FROM face_clusters WHERE embedding <=> :query_vector::vector < :threshold ORDER BY embedding <=> :query_vector::vector LIMIT :limit", nativeQuery = true)
    List<FaceCluster> findByVectorSimilarity(@Param("query_vector") String queryVector, @Param("threshold") double threshold, @Param("limit") int limit);
}
