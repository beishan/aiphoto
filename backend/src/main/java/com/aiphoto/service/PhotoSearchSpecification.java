package com.aiphoto.service;

import com.aiphoto.dto.SearchRequest;
import com.aiphoto.entity.Photo;
import com.aiphoto.entity.PhotoTag;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;

/** Runs filtering and ordering in PostgreSQL, without materializing the library. */
final class PhotoSearchSpecification {
  private PhotoSearchSpecification() {}

  static Specification<Photo> from(SearchRequest request) {
    return (root, query, cb) -> {
      var predicates = new ArrayList<Predicate>();
      predicates.add(cb.isNull(root.get("deletedAt")));
      if (request.getMinRating() != null) predicates.add(cb.ge(root.get("rating"), request.getMinRating()));
      if (request.getMaxRating() != null) predicates.add(cb.le(root.get("rating"), request.getMaxRating()));
      if (request.getInTimeline() != null) predicates.add(cb.equal(root.get("inTimeline"), request.getInTimeline()));
      if (request.getFolderId() != null) predicates.add(cb.equal(root.get("sourceFolderId"), request.getFolderId()));
      if (request.getFileType() != null && !request.getFileType().isBlank()) {
        try {
          predicates.add(cb.equal(root.get("mediaType"), Photo.MediaType.valueOf(request.getFileType())));
        } catch (IllegalArgumentException e) {
          predicates.add(cb.disjunction());
        }
      }
      if (request.getHasDescription() != null) {
        var blank = cb.equal(cb.trim(cb.coalesce(root.<String>get("note"), "")), "");
        predicates.add(request.getHasDescription() ? cb.not(blank) : blank);
      }
      LocalDate start = parseDate(request.getStartDate());
      LocalDate end = parseDate(request.getEndDate());
      if (start != null) predicates.add(cb.greaterThanOrEqualTo(root.get("exifDate"), start.atStartOfDay()));
      if (end != null) predicates.add(cb.lessThan(root.get("exifDate"), end.plusDays(1).atStartOfDay()));
      if (request.getTagId() != null) {
        var tags = query.subquery(Long.class);
        var tag = tags.from(PhotoTag.class);
        tags.select(tag.get("photoId")).where(cb.equal(tag.get("photoId"), root.get("id")), cb.equal(tag.get("tagId"), request.getTagId()));
        predicates.add(cb.exists(tags));
      }
      // Count queries must not sort; ID resolves equal sort values across pages.
      if (query.getResultType() != Long.class && query.getResultType() != long.class) {
        Expression<?> order = switch (request.getSortBy() == null ? "date" : request.getSortBy()) {
          case "rating" -> cb.coalesce(root.<Integer>get("rating"), 0);
          case "name" -> cb.coalesce(root.<String>get("originalFilename"), "");
          default -> cb.coalesce(root.<LocalDateTime>get("exifDate"), root.<LocalDateTime>get("createdAt"));
        };
        boolean desc = "desc".equals(request.getSortOrder());
        query.orderBy(desc ? cb.desc(order) : cb.asc(order), desc ? cb.desc(root.get("id")) : cb.asc(root.get("id")));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) return null;
    try { return LocalDate.parse(value); }
    catch (java.time.format.DateTimeParseException e) { return null; }
  }
}
