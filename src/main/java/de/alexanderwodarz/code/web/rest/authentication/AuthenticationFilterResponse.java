package de.alexanderwodarz.code.web.rest.authentication;

import lombok.Getter;
import lombok.Setter;
import org.json.JSONObject;

@Getter
@Setter
public class AuthenticationFilterResponse {

    private boolean access;
    private Authentication authentication;
    private Object error;
    private int code;

    public static AuthenticationFilterResponse UNAUTHORIZED() {
        AuthenticationFilterResponse response = new AuthenticationFilterResponse();
        response.setAccess(false);
        response.code = 401;
        response.setError(new JSONObject().put("error", "unauthorized"));
        return response;
    }

    public static AuthenticationFilterResponse OK() {
        AuthenticationFilterResponse response = new AuthenticationFilterResponse();
        response.setAccess(true);
        return response;
    }

    public AuthenticationFilterResponse setAuthentication(Authentication authentication) {
        this.authentication = authentication;
        return this;
    }

}
