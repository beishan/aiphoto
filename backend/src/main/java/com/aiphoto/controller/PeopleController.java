package com.aiphoto.controller;

import com.aiphoto.dto.FaceDTO;
import com.aiphoto.dto.PersonDTO;
import com.aiphoto.dto.PhotoDTO;
import com.aiphoto.service.FaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/people")
@RequiredArgsConstructor
public class PeopleController {

    private final FaceService faceService;

    @GetMapping
    public ResponseEntity<List<PersonDTO>> listPeople() {
        return ResponseEntity.ok(faceService.listPeople());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonDTO> getPerson(@PathVariable Long id) {
        return ResponseEntity.ok(faceService.getPerson(id));
    }

    @GetMapping("/{id}/photos")
    public ResponseEntity<Page<PhotoDTO>> getPersonPhotos(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size) {
        return ResponseEntity.ok(faceService.getPersonPhotos(id,
                org.springframework.data.domain.PageRequest.of(page, size)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PersonDTO> updatePerson(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(faceService.updatePersonName(id, body.get("name")));
    }

    @PostMapping("/merge")
    public ResponseEntity<Void> mergePeople(@RequestBody Map<String, Long> body) {
        faceService.mergePeople(body.get("targetId"), body.get("sourceId"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/recluster")
    public ResponseEntity<Map<String, Object>> recluster() {
        int merged = faceService.recluster();
        return ResponseEntity.ok(Map.of("merged", merged));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePerson(@PathVariable Long id) {
        faceService.deletePerson(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/faces")
    public ResponseEntity<List<FaceDTO>> getPersonFaces(@PathVariable Long id) {
        return ResponseEntity.ok(faceService.getPersonFaces(id));
    }

    @GetMapping("/faces/unassigned")
    public ResponseEntity<List<FaceDTO>> getUnnamedFaces() {
        return ResponseEntity.ok(faceService.getUnnamedFaces());
    }

    @PostMapping("/faces/{faceId}/assign")
    public ResponseEntity<Void> assignFace(
            @PathVariable Long faceId,
            @RequestBody Map<String, Long> body) {
        faceService.assignFaceToPerson(faceId, body.get("personId"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/cover-face")
    public ResponseEntity<Void> setCoverFace(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        faceService.setCoverFace(id, body.get("faceId"));
        return ResponseEntity.ok().build();
    }
}
