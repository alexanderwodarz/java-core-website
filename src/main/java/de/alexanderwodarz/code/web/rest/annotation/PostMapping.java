package de.alexanderwodarz.code.web.rest.annotation;

@RestRequest(method = "POST")
public @interface PostMapping {

    String path();

}
