package com.aiphoto.async;

import com.aiphoto.ai.AiThresholds;
import com.aiphoto.entity.Photo;
import com.aiphoto.entity.TrainingSet;
import com.aiphoto.repository.AlbumRepository;
import com.aiphoto.repository.PhotoRepository;
import com.aiphoto.repository.TrainingSetRepository;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrainingResultService {
    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final TrainingSetRepository trainingSetRepository;

    @Transactional(readOnly = true)
    public List<Photo> samples(Long albumId) {
        var album = albumRepository.findWithPhotos(albumId)
                .orElseThrow(() -> new IllegalArgumentException("相册不存在"));
        List<Photo> samples = album.getPhotos().stream().filter(photo -> photo.getDeletedAt() == null).toList();
        if (samples.isEmpty()) throw new IllegalArgumentException("请先向相册添加训练样本");
        return samples;
    }

    @Transactional
    public int complete(Long albumId, float[] centroid, double similarity) {
        double distance = AiThresholds.distance(similarity);
        var album = albumRepository.findForTraining(albumId)
                .orElseThrow(() -> new IllegalArgumentException("相册不存在"));
        var ids = new HashSet<>(album.getPhotos().stream().map(Photo::getId).toList());
        List<Photo> matches = photoRepository.findAllByVectorSimilarity(Arrays.toString(centroid), distance);
        int added = 0;
        for (Photo photo : matches) {
            if (ids.add(photo.getId())) {
                album.getPhotos().add(photo);
                added++;
            }
        }
        TrainingSet trainingSet = trainingSetRepository.findByAlbumId(albumId).orElse(new TrainingSet());
        trainingSet.setAlbum(album);
        trainingSet.setPrototypeVector(centroid);
        trainingSet.setThreshold(similarity);
        trainingSetRepository.save(trainingSet);
        albumRepository.save(album);
        return added;
    }
}
