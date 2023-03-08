package de.alexanderwodarz.code.web;

import java.net.Socket;

public interface WebServerListener {
    default void gotException(Exception e){
    }

    default void notFound(Socket connect, String path, String method){
    }

    default void request(Socket socket, String path, String method){
    }

    default void securityIssue(Socket socket, String path, String method, int code){
    }

}
