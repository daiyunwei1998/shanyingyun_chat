package org.service.customer.utils;


import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

public class JwtCookieUtil {
    public static void addJwtToCookie(String jwt, HttpServletResponse response) {
        Cookie jwtCookie = new Cookie("jwt", jwt);
        jwtCookie.setHttpOnly(true); // Prevents access via JavaScript
        // jwtCookie.setSecure(true);
        // TODO: Ensures it's only sent over HTTPS
        jwtCookie.setPath("/"); // Makes it accessible site-wide
        jwtCookie.setMaxAge(7 * 24 * 60 * 60); // Set expiration to 7 days (in seconds)

        response.addCookie(jwtCookie);
    }
}
