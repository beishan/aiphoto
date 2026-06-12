package com.memoryvault.controller;

import com.memoryvault.async.PhotoIndexingConsumer;
import com.memoryvault.config.RabbitMQConfig;
import com.memoryvault.dto.PhotoDTO;
import com.memoryvault.dto.PhotoDetailDTO;
import com.memoryvault.entity.AiTask;
import com.memoryvault.exception.DuplicateFileException;
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

    @PostMapping("/batch-upload")
    public ResponseEntity<List<Map<String, Object>>> batchUpload(
            @RequestParam("files") MultipartFile[] files,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        List<Map<String, Object>> results = new java.util.ArrayList<>();

        for (MultipartFile file : files) {
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("fileName", file.getOriginalFilename());
            try {
                PhotoDTO dto = photoService.uploadPhoto(file, userId);
                result.put("success", true);
                result.put("photo", dto);
            } catch (DuplicateFileException e) {
                result.put("success", false);
                result.put("error", "duplicate");
                result.put("message", e.getMessage());
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", "failed");
                result.put("message", e.getMessage());
            }
            results.add(result);
        }

        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PhotoDTO> getPhoto(@PathVariable Long id) {
        return ResponseEntity.ok(photoService.getPhoto(id));
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<PhotoDetailDTO> getPhotoDetail(@PathVariable Long id) {
        return ResponseEntity.ok(photoService.getPhotoDetail(id));
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

    @DeleteMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchDeletePhotos(@RequestBody List<Long> ids) {
        int success = 0;
        int fail = 0;
        for (Long id : ids) {
            try {
                photoService.deletePhoto(id);
                success++;
            } catch (Exception e) {
                fail++;
            }
        }
        return ResponseEntity.ok(Map.of("success", success, "fail", fail));
    }

    @PostMapping("/batch-favorite")
    public ResponseEntity<Map<String, Object>> batchFavorite(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<Number>) request.get("ids")).stream().map(Number::longValue).toList();
        Boolean favorite = (Boolean) request.get("favorite");
        int success = 0;
        for (Long id : ids) {
            try {
                PhotoDTO dto = new PhotoDTO();
                dto.setFavorite(favorite);
                photoService.updatePhoto(id, dto);
                success++;
            } catch (Exception e) {
                // skip
            }
        }
        return ResponseEntity.ok(Map.of("success", success, "total", ids.size()));
    }

    @PostMapping("/batch-rating")
    public ResponseEntity<Map<String, Object>> batchRating(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<Number>) request.get("ids")).stream().map(Number::longValue).toList();
        Integer rating = (Integer) request.get("rating");
        int success = 0;
        for (Long id : ids) {
            try {
                PhotoDTO dto = new PhotoDTO();
                dto.setRating(rating);
                photoService.updatePhoto(id, dto);
                success++;
            } catch (Exception e) {
                // skip
            }
        }
        return ResponseEntity.ok(Map.of("success", success, "total", ids.size()));
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
