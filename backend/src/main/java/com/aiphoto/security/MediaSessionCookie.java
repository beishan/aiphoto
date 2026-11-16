package com.aiphoto.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/** Browser image/video requests cannot attach Authorization headers. */
public final class MediaSessionCookie {
    private MediaSessionCookie() {}

    public static void write(HttpServletResponse response, HttpServletRequest request, String token) {
        // Session-only cookie; JWT expiration and disabled-account checks still apply per request.
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("aiphoto_media", token)
                .httpOnly(true).secure(request.isSecure() || "https".equals(request.getHeader("X-Forwarded-Proto")))
                .sameSite("Strict").path("/").build().toString());
    }

    public static void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("aiphoto_media", "")
                .httpOnly(true).sameSite("Strict").path("/").maxAge(0).build().toString());
    }
}
