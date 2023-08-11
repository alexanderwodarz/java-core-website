package de.alexanderwodarz.code.web.rest;

import de.alexanderwodarz.code.web.StatusCode;
import lombok.Getter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Getter
public class ResponseData {

    private String body;
    private int code;
    private File file;
    private List<String> headers = new ArrayList<>();

    public ResponseData(File file, StatusCode code){
        this.file = file;
        this.code = code.getCode();
    }

    public ResponseData(String body, int code) {
        this.body = body;
        this.code = code;
    }

    public ResponseData(String body, StatusCode code) {
        this.body = body;
        this.code = code.getCode();
    }

    public void addHeader(String line) {
        headers.add(line);
    }

}
