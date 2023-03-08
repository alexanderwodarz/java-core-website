package de.alexanderwodarz.code.web;

import de.alexanderwodarz.code.log.Log;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;

@WebServer(port = 80, name = "default", path = "./web", verbose = false)
public abstract class AbstractWebServer extends Thread implements WebServerListener {

    private String color;

    public WebServer getWebServer() {
        return getClass().getAnnotation(WebServer.class);
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    @Override
    public void run() {
        WebServer server = getClass().getAnnotation(WebServer.class);
        try {
            ServerSocket serverSocket;
            if (server.https())
                serverSocket = getServerSocket(server.port(), server.keyStoreLocation(), server.keyStorePassword());
            else
                serverSocket = new ServerSocket(server.port());

            while (true) {
                HttpConnection connection = new HttpConnection(serverSocket.accept(), server.path(), server.verbose(), this);
                Thread thread = new Thread(connection);
                thread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ServerSocket getServerSocket(int port, String location, String password) throws Exception {
        var keyStorePath = Path.of(location);
        char[] keyStorePassword = password.toCharArray();
        var serverSocket = getSslContext(keyStorePath, keyStorePassword)
                .getServerSocketFactory()
                .createServerSocket(port);
        Arrays.fill(keyStorePassword, '0');

        return serverSocket;
    }

    private SSLContext getSslContext(Path keyStorePath, char[] keyStorePass)
            throws Exception {

        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream(keyStorePath.toFile()), keyStorePass);

        var keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(keyStore, keyStorePass);

        var sslContext = SSLContext.getInstance("TLS");
        // Null means using default implementations for TrustManager and SecureRandom
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        return sslContext;
    }

    public void log(Object log) {
        log(log, false);
    }

    public void log(Object log, boolean error) {
        Log.log(log, getColor() + "www-" + getWebServer().name(), error);
    }

    public String applyRule(String requested) {
        if (getWebServer().type() == WebServerType.REST)
            return requested;
        if (requested.endsWith("/"))
            return requested + "index.html";
        return requested;
    }
}
