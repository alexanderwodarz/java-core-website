package de.alexanderwodarz.code.web.rest.authentication;

import de.alexanderwodarz.code.web.rest.RequestData;

public abstract class AuthenticationFilter {

    public static AuthenticationFilterResponse doFilter(RequestData request) {
        return null;
    }

    public static CorsResponse doCors(RequestData data) {
        return null;
    }

}
