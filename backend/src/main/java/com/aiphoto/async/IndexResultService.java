package com.aiphoto.async;

import com.aiphoto.ai.AiServiceClient;
import com.aiphoto.ai.YoloCategories;
import com.aiphoto.entity.*;
import com.aiphoto.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits each successful inference stage in a short, idempotent transaction. */
@Service
@RequiredArgsConstructor
public class IndexResultService {
    private final PhotoRepository photoRepository;
    private final FaceClusterRepository faceRepository;
    private final PersonRepository personRepository;
    private final TagRepository tagRepository;
    private final PhotoTagRepository photoTagRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveEmbedding(Long photoId, List<Float> embedding) {
        Photo photo = locked(photoId);
        if (photo.getEmbedding() != null) return;
        photo.setEmbedding(vector(embedding));
        photoRepository.save(photo);
    }

    @Transactional
    public void saveFaces(Long photoId, AiServiceClient.FaceDetectionResponse response, double distance) {
        // Serialize clustering across photos to avoid creating the same person concurrently.
        faceRepository.lockClustering();
        Photo photo = locked(photoId);
        if (photo.isFacesIndexed()) return;
        if (response == null || response.getFaces() == null) {
            throw new IllegalArgumentException("AI 服务未返回人脸结果");
        }
        Set<Long> people = new HashSet<>();
        List<FaceCluster> legacy = faceRepository.findByPhoto(photo);
        if (legacy.isEmpty()) {
            for (var face : response.getFaces()) {
                float[] embedding = vector(face.getEmbedding());
                List<Long> candidates = faceRepository.findPersonIdByVectorSimilarity(
                        vectorString(embedding), distance, 1);
                Person person = candidates.isEmpty() || candidates.get(0) == null ? null
                        : personRepository.findById(candidates.get(0)).orElse(null);
                if (person == null) person = new Person();
                if (photo.getExifDate() != null) {
                    if (person.getFirstSeen() == null || photo.getExifDate().isBefore(person.getFirstSeen())) {
                        person.setFirstSeen(photo.getExifDate());
                    }
                    if (person.getLastSeen() == null || photo.getExifDate().isAfter(person.getLastSeen())) {
                        person.setLastSeen(photo.getExifDate());
                    }
                }
                person = personRepository.save(person);
                FaceCluster cluster = new FaceCluster();
                cluster.setPhoto(photo);
                cluster.setPerson(person);
                cluster.setEmbedding(embedding);
                cluster.setConfidence(face.getConfidence());
                cluster.setBboxJson(json(face.getBbox()));
                cluster = faceRepository.saveAndFlush(cluster);
                if (person.getCoverFace() == null) {
                    person.setCoverFace(cluster);
                    personRepository.save(person);
                }
                people.add(person.getId());
            }
        } else {
            // Keep legacy/manual assignments; never insert a second copy on retry.
            for (FaceCluster face : legacy) {
                if (face.getPerson() != null) people.add(face.getPerson().getId());
            }
        }
        personRepository.flush();
        people.forEach(personRepository::refreshPhotoCount);
        photo.setFacesIndexed(true);
        photoRepository.save(photo);
    }

    @Transactional
    public void saveTags(Long photoId, AiServiceClient.ClassifyResponse response) {
        Photo photo = locked(photoId);
        if (photo.isTagsIndexed()) return;
        if (response == null || response.getTags() == null) {
            throw new IllegalArgumentException("AI 服务未返回目标标签");
        }
        Set<String> icons = new HashSet<>();
        for (var detection : response.getTags()) {
            String category = YoloCategories.category(detection.getName(), detection.getCategory());
            Tag tag = tagRepository.findByName(detection.getName()).orElseGet(() -> {
                Tag created = new Tag();
                created.setName(detection.getName());
                created.setType(Tag.TagType.AI);
                created.setCategory(category);
                return tagRepository.save(created);
            });
            if (tag.getType() != Tag.TagType.MANUAL) {
                tag.setCategory(category);
                tagRepository.save(tag);
            }
            PhotoTagId id = new PhotoTagId();
            id.setPhotoId(photoId);
            id.setTagId(tag.getId());
            PhotoTag link = photoTagRepository.findById(id).orElse(null);
            if (link == null || link.getSource() != Tag.TagType.MANUAL) {
                if (link == null) link = new PhotoTag();
                link.setPhotoId(photoId);
                link.setTagId(tag.getId());
                link.setSource(Tag.TagType.AI);
                link.setConfidence(detection.getConfidence());
                photoTagRepository.save(link);
            }
            String icon = YoloCategories.icon(detection.getName(), category);
            if (icon != null) icons.add(icon);
        }
        for (Category category : categoryRepository.findByIsSystemTrue()) {
            if (icons.contains(category.getIcon())) {
                categoryRepository.addPhotoToCategoryNative(category.getId(), photoId);
                categoryRepository.updatePhotoCount(category.getId());
            }
        }
        photo.setTagsIndexed(true);
        photoRepository.save(photo);
    }

    @Transactional
    public void saveCaption(Long photoId, String caption) {
        if (caption == null || caption.isBlank()) throw new IllegalArgumentException("AI 描述为空");
        Photo photo = locked(photoId);
        if (photo.isCaptionIndexed()) return;
        photo.setAiCaption(caption);
        photo.setCaptionIndexed(true);
        photoRepository.save(photo);
    }

    private Photo locked(Long id) {
        return photoRepository.findForIndexing(id)
                .orElseThrow(() -> new IllegalArgumentException("照片不存在或已删除"));
    }

    public static float[] vector(List<Float> values) {
        if (values == null || values.size() != 512) {
            throw new IllegalArgumentException("AI 向量必须为 512 维，请检查模型兼容性");
        }
        float[] result = new float[512];
        double norm = 0;
        for (int i = 0; i < result.length; i++) {
            Float value = values.get(i);
            if (value == null || !Float.isFinite(value)) {
                throw new IllegalArgumentException("AI 向量含无效数值");
            }
            result[i] = value;
            norm += value * (double) value;
        }
        if (norm == 0) throw new IllegalArgumentException("AI 向量不能为零向量");
        return result;
    }

    private String vectorString(float[] vector) {
        return java.util.Arrays.toString(vector);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法保存 AI 结果", exception);
        }
    }
}
