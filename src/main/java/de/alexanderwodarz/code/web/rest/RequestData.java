package de.alexanderwodarz.code.web.rest;

import de.alexanderwodarz.code.web.rest.authentication.Authentication;
import lombok.Getter;
import lombok.Setter;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

@Getter
@Setter
public class RequestData {

    private HashMap<String, String> headers = new HashMap<>();
    private HashMap<String, String> queries = new HashMap<>();
    private HashMap<String, String> variables = new HashMap<>();
    private List<Cookie> cookies = new ArrayList<>();
    private Authentication authentication;
    private String method, path, scheme, body, originalPath, originalMethod;
    private int level;
    private Socket socket;
    private long date;
    private Exception exception;

    public String getHeader(String header) {
        return headers.getOrDefault(header.toLowerCase(Locale.ROOT), null);
    }

    public String getAuthorization() {
        return getHeader("Authorization");
    }

}
