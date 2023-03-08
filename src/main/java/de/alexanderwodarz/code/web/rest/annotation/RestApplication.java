package de.alexanderwodarz.code.web.rest.annotation;

import de.alexanderwodarz.code.web.WebServer;
import de.alexanderwodarz.code.web.WebServerType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RestApplication {

}
