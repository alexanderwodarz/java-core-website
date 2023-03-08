package de.alexanderwodarz.code.web.rest;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;

@Getter
@Setter
public class FindPathResponse {

    private boolean found = false;
    private boolean exactMatch = false;
    private HashMap<String, String> variables = new HashMap<>();
    private HashMap<String, String> queries = new HashMap<>();

}
