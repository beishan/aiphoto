package com.memoryvault.service;

import com.memoryvault.dto.PersonDTO;
import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.entity.FaceCluster;
import com.memoryvault.entity.Person;
import com.memoryvault.entity.Photo;
import com.memoryvault.repository.FaceClusterRepository;
import com.memoryvault.repository.PersonRepository;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceService {

    private final PersonRepository personRepository;
    private final FaceClusterRepository faceClusterRepository;
    private final PhotoRepository photoRepository;
    private final MinioStorageService storageService;
    private final SettingService settingService;

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

    @Transactional
    public void deletePerson(Long id) {
        Person person = personRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Person not found"));

        // Unlink all face clusters from this person (keep faces in photos)
        List<FaceCluster> faces = faceClusterRepository.findByPerson(person);
        for (FaceCluster face : faces) {
            face.setPerson(null);
        }
        faceClusterRepository.saveAll(faces);

        // Delete the person
        personRepository.delete(person);
    }

    /**
     * Re-cluster all persons by computing average embeddings and merging similar ones.
     * Reads threshold from settings (ai_face_cluster_threshold), default 0.5.
     */
    @Transactional
    public int recluster() {
        String thresholdStr = settingService.getSetting(1L, "ai_face_cluster_threshold");
        double threshold = thresholdStr != null ? Double.parseDouble(thresholdStr) / 100.0 : 0.5;
        List<Person> persons = personRepository.findAll();
        if (persons.size() < 2) return 0;

        // Compute average embedding for each person
        Map<Person, float[]> centroids = new java.util.HashMap<>();
        for (Person person : persons) {
            List<FaceCluster> faces = faceClusterRepository.findByPerson(person);
            if (faces.isEmpty()) continue;
            float[] avg = computeAverageEmbedding(faces);
            if (avg != null) centroids.put(person, avg);
        }

        // Find and merge similar person pairs
        List<Person> personList = new ArrayList<>(centroids.keySet());
        int mergeCount = 0;
        boolean merged = true;

        // Iterate until no more merges possible
        while (merged) {
            merged = false;
            for (int i = 0; i < personList.size(); i++) {
                Person p1 = personList.get(i);
                float[] e1 = centroids.get(p1);
                if (e1 == null) continue;

                for (int j = i + 1; j < personList.size(); j++) {
                    Person p2 = personList.get(j);
                    float[] e2 = centroids.get(p2);
                    if (e2 == null) continue;

                    double distance = cosineDistance(e1, e2);
                    if (distance < threshold) {
                        log.info("Merging person {} into {} (distance: {})", p2.getId(), p1.getId(), distance);
                        // Merge p2 into p1
                        List<FaceCluster> p2Faces = faceClusterRepository.findByPerson(p2);
                        for (FaceCluster face : p2Faces) {
                            face.setPerson(p1);
                        }
                        faceClusterRepository.saveAll(p2Faces);
                        p1.setPhotoCount(p1.getPhotoCount() + p2.getPhotoCount());
                        personRepository.save(p1);
                        personRepository.delete(p2);

                        // Update centroids - recompute for p1
                        List<FaceCluster> p1Faces = faceClusterRepository.findByPerson(p1);
                        float[] newAvg = computeAverageEmbedding(p1Faces);
                        centroids.put(p1, newAvg);
                        centroids.remove(p2);
                        personList.remove(j);
                        j--;
                        mergeCount++;
                        merged = true;
                    }
                }
            }
        }

        return mergeCount;
    }

    private float[] computeAverageEmbedding(List<FaceCluster> faces) {
        if (faces.isEmpty()) return null;
        float[] sum = new float[512];
        int count = 0;
        for (FaceCluster face : faces) {
            float[] emb = face.getEmbedding();
            if (emb != null) {
                for (int i = 0; i < 512; i++) {
                    sum[i] += emb[i];
                }
                count++;
            }
        }
        if (count == 0) return null;
        for (int i = 0; i < 512; i++) {
            sum[i] /= count;
        }
        return sum;
    }

    private double cosineDistance(float[] a, float[] b) {
        if (a.length != b.length) return 1.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 1.0;
        return 1.0 - (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    public Page<PhotoDTO> getPersonPhotos(Long personId, Pageable pageable) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new RuntimeException("Person not found"));

        // Get all face clusters for this person, extract unique photos
        List<FaceCluster> faces = faceClusterRepository.findByPerson(person);
        List<Photo> photos = faces.stream()
                .map(FaceCluster::getPhoto)
                .distinct()
                .collect(Collectors.toList());

        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), photos.size());
        List<PhotoDTO> pageContent = photos.subList(start, end).stream()
                .map(this::toPhotoDTO)
                .toList();

        return new PageImpl<>(pageContent, pageable, photos.size());
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
                Photo photo = person.getCoverFace().getPhoto();
                try {
                    dto.setCoverPhotoUrl(storageService.getPhotoUrl(photo.getFilePath()));
                } catch (Exception e) {
                    log.warn("Failed to resolve cover photo URL for person {}", person.getId());
                }
            }
        }
        return dto;
    }

    private PhotoDTO toPhotoDTO(Photo photo) {
        PhotoDTO dto = new PhotoDTO();
        dto.setId(photo.getId());
        dto.setFilePath(photo.getFilePath());
        dto.setExifDate(photo.getExifDate());
        dto.setGpsLat(photo.getGpsLat());
        dto.setGpsLng(photo.getGpsLng());
        dto.setRating(photo.getRating());
        dto.setNote(photo.getNote());
        dto.setAiCaption(photo.getAiCaption());
        dto.setWidth(photo.getWidth());
        dto.setHeight(photo.getHeight());
        dto.setFileSize(photo.getFileSize());
        dto.setMediaType(photo.getMediaType().name());
        dto.setFavorite(photo.getFavorite());
        dto.setOriginalFilename(photo.getOriginalFilename());
        dto.setCreatedAt(photo.getCreatedAt());
        try {
            String thumbExt = photo.getOriginalFilename() != null
                    && photo.getOriginalFilename().toLowerCase().endsWith(".webp") ? "webp" : "jpg";
            dto.setThumbnailUrl(storageService.getThumbnailUrl(
                    photo.getFileHashMd5() + "/thumb." + thumbExt));
            dto.setOriginalUrl(storageService.getPhotoUrl(photo.getFilePath()));
        } catch (Exception e) {
            log.warn("Failed to resolve URLs for photo {}", photo.getId());
        }
        return dto;
    }
}
