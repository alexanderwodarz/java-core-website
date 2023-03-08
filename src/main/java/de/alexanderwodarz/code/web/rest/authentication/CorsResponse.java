package de.alexanderwodarz.code.web.rest.authentication;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CorsResponse {

    private String origin, methods = "GET, POST, PUT, DELETE, OPTIONS", headers = "authorization, content-type";
    private int age = 3600;
    private boolean credentials = false;

    public List<String> getHeaders() {
        List<String> headers = new ArrayList<>();
        headers.add("Access-Control-Allow-Origin: " + origin);
        headers.add("Access-Control-Allow-Methods: " + methods);
        headers.add("Access-Control-Max-Age: " + age);
        headers.add("Access-Control-Allow-Headers: " + this.headers);
        headers.add("Access-Control-Allow-Credentials: " + credentials);
        return headers;
    }

}
