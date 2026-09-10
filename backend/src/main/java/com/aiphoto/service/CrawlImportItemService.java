package com.aiphoto.service;

import com.aiphoto.async.PhotoIndexingService;
import com.aiphoto.entity.AiTask;
import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.entity.Photo;
import com.aiphoto.entity.PhotoSource;
import com.aiphoto.entity.Album;
import com.aiphoto.entity.PhotoTag;
import com.aiphoto.entity.PhotoTagId;
import com.aiphoto.repository.AiTaskRepository;
import com.aiphoto.repository.CrawlAssetRepository;
import com.aiphoto.repository.PhotoRepository;
import com.aiphoto.repository.PhotoSourceRepository;
import com.aiphoto.repository.AlbumRepository;
import com.aiphoto.repository.PhotoTagRepository;
import com.aiphoto.repository.TagRepository;
import com.aiphoto.storage.LocalStorageService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class CrawlImportItemService {

    private final CrawlAssetRepository assetRepository;
    private final PhotoRepository photoRepository;
    private final PhotoSourceRepository sourceRepository;
    private final CrawlStagingStorageService stagingStorage;
    private final LocalStorageService storageService;
    private final AiTaskRepository aiTaskRepository;
    private final PhotoIndexingService indexingService;
    private final AlbumRepository albumRepository;
    private final TagRepository tagRepository;
    private final PhotoTagRepository photoTagRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportOutcome importOne(
            Long assetId, Long jobId, Long ownerId, Long albumId, java.util.List<Long> tagIds) throws Exception {
        CrawlAsset asset = assetRepository.findByIdAndJobId(assetId, jobId)
                .orElseThrow(() -> new IllegalArgumentException("采集图片不存在"));
        if (asset.getStatus() == CrawlAsset.Status.IMPORTED) {
            return new ImportOutcome(Result.SUCCESS, asset.getImportedPhotoId());
        }
        if (asset.getStatus() != CrawlAsset.Status.DOWNLOADED) {
            throw new IllegalStateException("图片未处于可入库状态");
        }
        photoRepository.lockFileHash(asset.getFileHashMd5());
        var duplicate = photoRepository.findByFileHashMd5(asset.getFileHashMd5());
        if (duplicate.isPresent()) {
            asset.setStatus(CrawlAsset.Status.DUPLICATE);
            asset.setImportedPhotoId(duplicate.get().getId());
            assetRepository.save(asset);
            if (duplicate.get().getDeletedAt() == null) {
                organize(duplicate.get(), ownerId, albumId, tagIds, asset.getNote());
            }
            return new ImportOutcome(Result.SKIPPED, duplicate.get().getId());
        }

        byte[] bytes = stagingStorage.read(asset.getLocalPath());
        String filename = asset.getOriginalFilename() == null ? "crawl-" + assetId + ".jpg"
                : asset.getOriginalFilename();
        String objectName = asset.getFileHashMd5() + "/" + filename;
        storageService.uploadPhoto(bytes, objectName, asset.getContentType());
        String thumbExtension = "image/webp".equals(asset.getContentType()) ? "webp" : "jpg";
        byte[] thumbnail = bytes;
        if (!"image/webp".equals(asset.getContentType()) && ImageIO.read(new ByteArrayInputStream(bytes)) != null) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(bytes)).size(400, 400)
                    .outputFormat("jpg").outputQuality(0.8).toOutputStream(output);
            thumbnail = output.toByteArray();
        }
        storageService.uploadThumbnail(thumbnail, asset.getFileHashMd5() + "/thumb." + thumbExtension);

        Photo photo = new Photo();
        photo.setFilePath(objectName);
        photo.setFileHashMd5(asset.getFileHashMd5());
        photo.setFileHashPhash(asset.getFileHashPhash());
        photo.setFileSize(asset.getFileSize());
        photo.setWidth(asset.getWidth());
        photo.setHeight(asset.getHeight());
        photo.setOriginalFilename(filename);
        photo.setMediaType(filename.toLowerCase().endsWith(".gif") ? Photo.MediaType.GIF : Photo.MediaType.PHOTO);
        photo.setNote(asset.getNote());
        photo = photoRepository.save(photo);
        organize(photo, ownerId, albumId, tagIds, asset.getNote());

        PhotoSource source = new PhotoSource();
        source.setPhotoId(photo.getId());
        source.setPageUrl(asset.getSourcePageUrl());
        source.setImageUrl(asset.getImageUrl());
        sourceRepository.save(source);
        asset.setStatus(CrawlAsset.Status.IMPORTED);
        asset.setImportedPhotoId(photo.getId());
        assetRepository.save(asset);

        AiTask task = new AiTask();
        task.setType(AiTask.TaskType.INDEX);
        task.setPhotoIdsJson("[" + photo.getId() + "]");
        task = aiTaskRepository.save(task);
        Long taskId = task.getId();
        Long photoId = photo.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                indexingService.indexPhotos(taskId, java.util.List.of(photoId));
            }
        });
        return new ImportOutcome(Result.SUCCESS, photo.getId());
    }

    public enum Result { SUCCESS, SKIPPED }
    public record ImportOutcome(Result result, Long photoId) {}

    private void organize(Photo photo, Long ownerId, Long albumId, java.util.List<Long> tagIds, String note) {
        if (note != null && !note.isBlank()) {
            photo.setNote(note);
            photoRepository.save(photo);
        }
        if (albumId != null) {
            Album album = albumRepository.findById(albumId)
                    .orElseThrow(() -> new IllegalArgumentException("目标相册不存在"));
            if (album.getOwner() != null && !album.getOwner().getId().equals(ownerId)) {
                throw new IllegalArgumentException("无权写入目标相册");
            }
            if (!album.getPhotos().contains(photo)) album.getPhotos().add(photo);
            albumRepository.save(album);
        }
        for (Long tagId : tagIds == null ? java.util.List.<Long>of() : tagIds.stream().distinct().toList()) {
            if (!tagRepository.existsById(tagId)) throw new IllegalArgumentException("标签不存在: " + tagId);
            PhotoTagId id = new PhotoTagId();
            id.setPhotoId(photo.getId());
            id.setTagId(tagId);
            if (!photoTagRepository.existsById(id)) {
                PhotoTag photoTag = new PhotoTag();
                photoTag.setPhotoId(photo.getId());
                photoTag.setTagId(tagId);
                photoTagRepository.save(photoTag);
            }
        }
    }
}
