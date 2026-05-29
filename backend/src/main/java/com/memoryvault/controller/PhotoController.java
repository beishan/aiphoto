package com.memoryvault.controller;

import com.memoryvault.async.PhotoIndexingConsumer;
import com.memoryvault.config.RabbitMQConfig;
import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.entity.AiTask;
import com.memoryvault.entity.Photo;
import com.memoryvault.entity.User;
import com.memoryvault.repository.AiTaskRepository;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.repository.UserRepository;
import com.memoryvault.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final AiTaskRepository aiTaskRepository;
    private final RabbitTemplate rabbitTemplate;

    @GetMapping
    public ResponseEntity<Page<PhotoDTO>> listPhotos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(photoService.listPhotos(PageRequest.of(page, size)));
    }

    @PostMapping("/upload")
    public ResponseEntity<PhotoDTO> uploadPhoto(@RequestParam("file") MultipartFile file, Authentication authentication) throws Exception {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(photoService.uploadPhoto(file, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PhotoDTO> getPhoto(@PathVariable Long id) {
        return ResponseEntity.ok(photoService.getPhoto(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PhotoDTO> updatePhoto(@PathVariable Long id, @RequestBody PhotoDTO updates) {
        return ResponseEntity.ok(photoService.updatePhoto(id, updates));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long id) {
        photoService.deletePhoto(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/favorites")
    public ResponseEntity<Page<PhotoDTO>> getFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(photoService.getFavorites(PageRequest.of(page, size)));
    }

    @GetMapping("/rated")
    public ResponseEntity<Page<PhotoDTO>> getByRating(
            @RequestParam(defaultValue = "3") int minRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(photoService.getByRating(minRating, PageRequest.of(page, size)));
    }

    private Long getUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }

    @PostMapping("/index-all")
    public ResponseEntity<Map<String, Object>> indexAllPhotos() {
        List<Photo> photos = photoRepository.findByEmbeddingIsNull();
        if (photos.isEmpty()) {
            return ResponseEntity.ok(Map.of("taskId", 0, "count", 0, "message", "No photos to index"));
        }

        List<Long> photoIds = photos.stream().map(Photo::getId).collect(Collectors.toList());

        AiTask aiTask = new AiTask();
        aiTask.setType(AiTask.TaskType.INDEX);
        aiTask.setPhotoIdsJson(photoIds.toString());
        aiTask = aiTaskRepository.save(aiTask);

        PhotoIndexingConsumer.PhotoIndexMessage message = new PhotoIndexingConsumer.PhotoIndexMessage();
        message.setTaskId(aiTask.getId());
        message.setPhotoIds(photoIds);
        rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_PHOTO_INDEX, message);

        return ResponseEntity.ok(Map.of("taskId", aiTask.getId(), "count", photoIds.size()));
    }
}
