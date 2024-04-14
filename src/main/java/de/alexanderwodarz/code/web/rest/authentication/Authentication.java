package de.alexanderwodarz.code.web.rest.authentication;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class Authentication {

    private String name;
    private String path;
    private String method;

}
