package com.memoryvault.repository;

import com.memoryvault.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PersonRepository extends JpaRepository<Person, Long> {

    @Query("SELECT p FROM Person p WHERE p.name IS NOT NULL ORDER BY p.photoCount DESC")
    List<Person> findNamed();

    @Query("SELECT p FROM Person p WHERE p.name IS NULL ORDER BY p.photoCount DESC")
    List<Person> findUnnamed();

    List<Person> findByNameContainingIgnoreCase(String name);
}
