package com.aiphoto.controller;

import com.aiphoto.storage.StorageMigrationService;
import com.aiphoto.storage.StorageRoots.Kind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/storage-locations")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class StorageMigrationController {
    private final StorageMigrationService service;

    @GetMapping
    public StorageMigrationService.Overview overview() throws IOException {
        return service.overview();
    }

    @GetMapping("/directories")
    public StorageMigrationService.DirectoryView browse(@RequestParam(defaultValue = "") String directory) throws IOException {
        return service.browse(directory);
    }

    @PostMapping("/directories")
    @ResponseStatus(HttpStatus.CREATED)
    public StorageMigrationService.DirectoryEntry create(@Valid @RequestBody DirectoryRequest request) throws IOException {
        return service.createDirectory(request.parent(), request.name());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StorageMigrationService.Overview add(@Valid @RequestBody Location request) throws IOException {
        service.add(request.kind(), request.directory());
        return service.overview();
    }

    @PostMapping("/migrate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StorageMigrationService.Job migrate(@Valid @RequestBody Location request) throws IOException {
        return service.start(request.kind(), request.directory());
    }

    public record Location(@NotNull Kind kind, @NotBlank String directory) {}
    public record DirectoryRequest(@NotNull String parent, @NotBlank String name) {}
}
