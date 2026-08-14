package com.aiphoto.repository;

import com.aiphoto.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PersonRepository extends JpaRepository<Person, Long> {

    @Query("SELECT p FROM Person p WHERE p.name IS NOT NULL ORDER BY p.photoCount DESC")
    List<Person> findNamed();

    @Query("SELECT p FROM Person p WHERE p.name IS NULL ORDER BY p.photoCount DESC")
    List<Person> findUnnamed();

    List<Person> findByNameContainingIgnoreCase(String name);

    @Modifying
    @Query("UPDATE Person p SET p.coverFace = NULL WHERE p.coverFace.id IN :faceIds")
    void clearCoverFaceRefs(@Param("faceIds") List<Long> faceIds);

    @Modifying
    @Query(value = "UPDATE people SET cover_face_id = :faceId WHERE id = :personId", nativeQuery = true)
    void setCoverFaceId(@Param("personId") Long personId, @Param("faceId") Long faceId);
}
