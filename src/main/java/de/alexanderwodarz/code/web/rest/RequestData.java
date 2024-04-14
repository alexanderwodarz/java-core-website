package de.alexanderwodarz.code.web.rest;

import lombok.Getter;
import lombok.Setter;

import java.net.Socket;
import java.util.HashMap;
import java.util.Locale;

@Getter
@Setter
public class RequestData {

    private HashMap<String, String> headers = new HashMap<>();
    private HashMap<String, String> queries = new HashMap<>();
    private HashMap<String, String> variables = new HashMap<>();
    private String method, path, scheme, body, originalPath, originalMethod;
    private int level;
    private Socket socket;
    private Exception exception;

    public String getHeader(String header) {
        return headers.getOrDefault(header.toLowerCase(Locale.ROOT), null);
    }

    public String getAuthorization() {
        return getHeader("Authorization");
    }

}
