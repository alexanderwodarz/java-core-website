package de.alexanderwodarz.code.web.rest;

import de.alexanderwodarz.code.web.rest.annotation.RestController;
import de.alexanderwodarz.code.web.rest.annotation.RestRequest;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Method;

@Getter
@Setter
public class RestWebRequest {

    private RestRequest request;
    private RestController controller;
    private Method method;
    private FindPathResponse findPathResponse;

    public String getProduces() {
        if (!request.produces().isEmpty())
            return request.produces();
        if (!controller.produces().isEmpty())
            return controller.produces();
        return "application/html";
    }

}
