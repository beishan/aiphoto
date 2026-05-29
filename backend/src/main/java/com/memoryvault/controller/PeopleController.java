package com.memoryvault.controller;

import com.memoryvault.dto.PersonDTO;
import com.memoryvault.service.FaceService;
import lombok.RequiredArgsConstructor;
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

    @PutMapping("/{id}")
    public ResponseEntity<PersonDTO> updatePerson(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(faceService.updatePersonName(id, body.get("name")));
    }

    @PostMapping("/merge")
    public ResponseEntity<Void> mergePeople(@RequestBody Map<String, Long> body) {
        faceService.mergePeople(body.get("targetId"), body.get("sourceId"));
        return ResponseEntity.ok().build();
    }
}
