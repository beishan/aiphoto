package com.aiphoto.dto;

import java.util.List;

public record PhotoCursorPage(List<PhotoDTO> content, Long nextCursor, boolean hasMore) {}
