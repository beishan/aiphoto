package com.memoryvault.service;

import com.memoryvault.async.TrainingTaskConsumer;
import com.memoryvault.config.RabbitMQConfig;
import com.memoryvault.dto.AlbumDTO;
import com.memoryvault.entity.*;
import com.memoryvault.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    private final AiTaskRepository aiTaskRepository;

    public List<AlbumDTO> listAlbums() {
        return albumRepository.findAll().stream().map(this::toDTO).toList();
    }

    public AlbumDTO getAlbum(Long id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Album not found"));
        return toDTO(album);
    }

    @Transactional
    public AlbumDTO createAlbum(AlbumDTO albumDTO) {
        Album album = new Album();
        album.setName(albumDTO.getName());
        album.setType(Album.AlbumType.valueOf(albumDTO.getType()));
        album.setShared(albumDTO.getShared() != null ? albumDTO.getShared() : false);
        album.setBirthDate(albumDTO.getBirthDate());
        album = albumRepository.save(album);
        return toDTO(album);
    }

    @Transactional
    public void addPhotoToAlbum(Long albumId, Long photoId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("Album not found"));
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found"));
        album.getPhotos().add(photo);
        albumRepository.save(album);
    }

    @Transactional
    public void removePhotoFromAlbum(Long albumId, Long photoId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("Album not found"));
        album.getPhotos().removeIf(p -> p.getId().equals(photoId));
        albumRepository.save(album);
    }

    @Transactional
    public void deleteAlbum(Long id) {
        albumRepository.deleteById(id);
    }

    public Map<String, Object> trainAlbum(Long albumId, Double threshold) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("Album not found"));

        AiTask aiTask = new AiTask();
        aiTask.setType(AiTask.TaskType.TRAIN);
        aiTask = aiTaskRepository.save(aiTask);

        TrainingTaskConsumer.TrainingMessage message = new TrainingTaskConsumer.TrainingMessage();
        message.setTaskId(aiTask.getId());
        message.setAlbumId(albumId);
        message.setThreshold(threshold);
        rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_TRAINING, message);

        return Map.of("taskId", aiTask.getId(), "message", "Training task created");
    }

    private AlbumDTO toDTO(Album album) {
        AlbumDTO dto = new AlbumDTO();
        dto.setId(album.getId());
        dto.setName(album.getName());
        dto.setType(album.getType().name());
        dto.setShared(album.getShared());
        dto.setBirthDate(album.getBirthDate());
        dto.setPhotoCount(album.getPhotos().size());
        if (album.getCoverPhoto() != null) {
            dto.setCoverPhotoId(album.getCoverPhoto().getId());
        }
        dto.setCreatedAt(album.getCreatedAt());
        return dto;
    }
}
