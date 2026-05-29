package com.memoryvault.service;

import com.memoryvault.dto.PersonDTO;
import com.memoryvault.entity.FaceCluster;
import com.memoryvault.entity.Person;
import com.memoryvault.entity.Photo;
import com.memoryvault.repository.FaceClusterRepository;
import com.memoryvault.repository.PersonRepository;
import com.memoryvault.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FaceService {

    private final PersonRepository personRepository;
    private final FaceClusterRepository faceClusterRepository;
    private final PhotoRepository photoRepository;

    public List<PersonDTO> listPeople() {
        return personRepository.findAll().stream().map(this::toDTO).toList();
    }

    public List<PersonDTO> listNamedPeople() {
        return personRepository.findNamed().stream().map(this::toDTO).toList();
    }

    public PersonDTO getPerson(Long id) {
        Person person = personRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Person not found"));
        return toDTO(person);
    }

    @Transactional
    public PersonDTO updatePersonName(Long id, String name) {
        Person person = personRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Person not found"));
        person.setName(name);
        person = personRepository.save(person);
        return toDTO(person);
    }

    @Transactional
    public void mergePeople(Long targetId, Long sourceId) {
        Person target = personRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Target person not found"));
        Person source = personRepository.findById(sourceId)
                .orElseThrow(() -> new RuntimeException("Source person not found"));

        // Reassign all faces from source to target
        List<FaceCluster> sourceFaces = faceClusterRepository.findByPerson(source);
        for (FaceCluster face : sourceFaces) {
            face.setPerson(target);
        }
        faceClusterRepository.saveAll(sourceFaces);

        // Update photo count
        target.setPhotoCount(target.getPhotoCount() + source.getPhotoCount());
        personRepository.save(target);

        // Delete source
        personRepository.delete(source);
    }

    private PersonDTO toDTO(Person person) {
        PersonDTO dto = new PersonDTO();
        dto.setId(person.getId());
        dto.setName(person.getName());
        dto.setPhotoCount(person.getPhotoCount());
        dto.setFirstSeen(person.getFirstSeen());
        dto.setLastSeen(person.getLastSeen());
        if (person.getCoverFace() != null) {
            dto.setCoverFaceId(person.getCoverFace().getId());
            if (person.getCoverFace().getPhoto() != null) {
                dto.setCoverPhotoUrl(person.getCoverFace().getPhoto().getFilePath());
            }
        }
        return dto;
    }
}
