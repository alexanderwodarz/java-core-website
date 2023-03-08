package de.alexanderwodarz.code.web;

import de.alexanderwodarz.code.JavaCore;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface WebServer {

    int port() default 8080;
    boolean verbose() default false;
    String path() default "";
    String notFound() default "/404.html";
    String name() default "www";
    boolean https() default false;
    String keyStoreLocation() default "";
    String keyStorePassword() default "";
    WebServerType type() default WebServerType.WEB;

}
