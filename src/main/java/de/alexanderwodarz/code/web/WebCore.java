package de.alexanderwodarz.code.web;

import de.alexanderwodarz.code.log.Color;
import de.alexanderwodarz.code.log.Log;
import de.alexanderwodarz.code.model.varible.Varible;
import de.alexanderwodarz.code.model.varible.VaribleMap;
import de.alexanderwodarz.code.web.rest.RestHandler;
import de.alexanderwodarz.code.web.rest.RestWebRequest;
import de.alexanderwodarz.code.web.rest.annotation.RestApplication;
import de.alexanderwodarz.code.web.rest.annotation.RestController;
import de.alexanderwodarz.code.web.rest.annotation.RestRequest;
import de.alexanderwodarz.code.web.rest.authentication.AuthenticationFilter;
import org.reflections.Reflections;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WebCore {
    private static Color[] colors = new Color[]{Color.BRIGHT_BLACK, Color.BRIGHT_RED, Color.BRIGHT_GREEN, Color.BRIGHT_YELLOW, Color.BRIGHT_BLUE, Color.BRIGHT_PURPLE, Color.BRIGHT_CYAN, Color.BLACK, Color.RED, Color.GREEN, Color.YELLOW, Color.BLUE, Color.PURPLE, Color.CYAN,};
    private static ArrayList<RestWebRequest> requests = new ArrayList<>();
    private static Class<? extends AuthenticationFilter> filter;

    public static void addWebServer(AbstractWebServer abstractServer) throws FileNotFoundException {
        if (!abstractServer.getClass().isAnnotationPresent(WebServer.class)) return;
        WebServer webServer = abstractServer.getWebServer();
        if (webServer.type() == WebServerType.WEB) {
            File notFound = new File(webServer.path() + webServer.notFound());
            if (!notFound.exists()) {
                throw new FileNotFoundException("Die 404 Seite wurde nicht gefunden(" + notFound.getAbsolutePath() + ")");
            }
        }
        Thread thread = new Thread(abstractServer);
        thread.start();
        abstractServer.setColor(colors[new Random().nextInt(colors.length)].toString());
        Log.log("Server successfully started", abstractServer.getColor() + "www-" + webServer.name(), false);
    }

    public static Class<? extends AuthenticationFilter> getFilter() {
        return filter;
    }

    public static void start(Class clazz) throws Exception {
        start(clazz, new VaribleMap().put().setKey("port").setValue(8080).build());
    }

    public static void start(Class clazz, VaribleMap map) throws Exception {
        if (!clazz.isAnnotationPresent(RestApplication.class))
            throw new Exception("RestApplication Annotation not found");
        String packageName = clazz.getPackage().getName();
        if (packageName.length() == 0) throw new Exception("Create a package or move class to a package");
        Reflections reflections = new Reflections(packageName);
        filter = reflections.getSubTypesOf(AuthenticationFilter.class).stream().findFirst().orElse(null);
        for (Class<?> aClass : reflections.getTypesAnnotatedWith(RestController.class)) {
            RestController controller = aClass.getAnnotation(RestController.class);
            List<Method> methods = new ArrayList<>();
            if (!controller.extend().getName().equals("java.lang.Object"))
                for (Method declaredMethod : controller.extend().getDeclaredMethods())
                    if (declaredMethod.isAnnotationPresent(RestRequest.class))
                        methods.add(declaredMethod);
            for (Method declaredMethod : aClass.getDeclaredMethods())
                if (declaredMethod.isAnnotationPresent(RestRequest.class))
                    methods.add(declaredMethod);
            for (Method method : methods) {
                RestWebRequest request = new RestWebRequest();
                request.setRequest(method.getAnnotation(RestRequest.class));
                request.setController(aClass.getAnnotation(RestController.class));
                request.setMethod(method);
                requests.add(request);
            }
        }
        ServerSocket serverSocket = getSocket(map);
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    RestHandler handler = new RestHandler(serverSocket.accept());
                    Thread t = new Thread(handler);
                    t.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    private static ServerSocket getSocket(VaribleMap map) throws IOException {
        AtomicInteger port = new AtomicInteger(8080);
        map.getVaribles().stream().filter(v -> v.getKey().equals("port")).findFirst().ifPresent(v -> port.set(Integer.parseInt(v.getValue().toString())));
        AtomicBoolean https = new AtomicBoolean(false);
        map.getVaribles().stream().filter(v -> v.getKey().equals("https")).findFirst().ifPresent(b -> https.set(Boolean.parseBoolean(b.getValue().toString())));
        Varible location = map.getVaribles().stream().filter(v -> v.getKey().equals("location")).findFirst().orElse(null);
        Varible password = map.getVaribles().stream().filter(v -> v.getKey().equals("password")).findFirst().orElse(null);
        if (https.get() && location != null && password != null) {
            try {
                return getServerSocket(port.get(), location.getValue().toString(), password.getValue().toString());
            } catch (Exception ignored) {
            }
        }
        return new ServerSocket(port.get());
    }

    private static ServerSocket getServerSocket(int port, String location, String password) throws Exception {
        var keyStorePath = Path.of(location);
        char[] keyStorePassword = password.toCharArray();
        var serverSocket = getSslContext(keyStorePath, keyStorePassword)
                .getServerSocketFactory()
                .createServerSocket(port);
        Arrays.fill(keyStorePassword, '0');

        return serverSocket;
    }

    private static SSLContext getSslContext(Path keyStorePath, char[] keyStorePass)
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

    public static ArrayList<RestWebRequest> getRequests() {
        return requests;
    }
}
