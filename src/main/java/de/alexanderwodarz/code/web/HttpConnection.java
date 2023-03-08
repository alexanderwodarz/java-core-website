package de.alexanderwodarz.code.web;

import lombok.SneakyThrows;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class HttpConnection implements Runnable {

    private File WEB_ROOT;
    private boolean verbose;
    private Socket connect;
    private AbstractWebServer webServer;

    public HttpConnection(Socket c, String root, boolean verbose, AbstractWebServer webServer) {
        connect = c;
        this.WEB_ROOT = new File(root);
        this.verbose = verbose;
        this.webServer = webServer;
    }

    @Override
    public void run() {
        BufferedReader in = null;
        PrintWriter out = null;
        BufferedOutputStream dataOut = null;
        String fileRequested = null;

        try {
            in = new BufferedReader(new InputStreamReader(connect.getInputStream()));
            out = new PrintWriter(connect.getOutputStream(), true, StandardCharsets.UTF_8);
            dataOut = new BufferedOutputStream(connect.getOutputStream());

            String input = in.readLine();
            if (input == null) {
                return;
            }
            String method = input.split(" ")[0];
            fileRequested = webServer.applyRule(input.split(" ")[1]);
            System.out.println(fileRequested);

            webServer.request(connect, fileRequested, method);

            if (fileRequested.contains("..")) {
                locked(connect, fileRequested, method, dataOut, out);
                return;
            }

            if (webServer.getWebServer().type().equals(WebServerType.WEB)) {
                if (isAsset(fileRequested)) {
                    showAsset(fileRequested, method, dataOut, out);
                } else {
                    fileNotFound(connect, dataOut, out, fileRequested, method);
                }
            } else {
                System.out.println(fileRequested);
            }
        } catch (FileNotFoundException fnfe) {
            try {
                fileNotFound(connect, dataOut, out, fileRequested, "");
            } catch (IOException ioe) {
                webServer.gotException(ioe);
            }
        } catch (IOException ioe) {
            System.err.println("Server error : " + ioe);
        } finally {
            try {
                in.close();
                out.close();
                dataOut.close();
                connect.close();
            } catch (Exception e) {
                webServer.gotException(e);
            }

            if (verbose) {
                System.out.println("Connection closed.\n");
            }
        }
    }

    private void showAsset(String path, String method, BufferedOutputStream dataOut, PrintWriter out) throws IOException {
        File file = new File(WEB_ROOT, path);

        String content = getContentType(path);

        if (method.equals("GET"))
            print(file, new String(readFileData(file, (int) file.length())), content, dataOut, out, 200);

        if (verbose)
            System.out.println("File " + path + " of type " + content + " returned");

    }

    @SneakyThrows
    private void print(File file, String content, String contentType, BufferedOutputStream dataOut, PrintWriter out, int status) {
        int fileLength = 0;
        byte[] fileData = null;
        boolean image = false;
        if (contentType.contains("html") || contentType.contains("javascript")) {
            fileData = content.getBytes(StandardCharsets.UTF_8);
            fileLength = content.getBytes().length;
        } else if (contentType.equals("image/png")) {
            image = true;
        } else {
            fileData = content.getBytes(StandardCharsets.ISO_8859_1);
            fileLength = fileData.length;
        }

        out.println("HTTP/1.1 " + status);
        out.println("Server: Java HTTP von Alex lol");
        out.println("Date: " + new Date());
        out.println("Content-type: " + contentType);
        out.println("Connection: Keep-Alive");
        out.println();
        out.flush();

        if (image) {
            BufferedImage img = ImageIO.read(file);
            ImageIO.write(img, contentType.split("/")[1], dataOut);
            return;
        }

        try {
            dataOut.write(fileData, 0, fileLength);
            dataOut.flush();
        } catch (Exception e) {
            webServer.gotException(e);
        }
    }

    private boolean isAsset(String path) {
        File file = new File(WEB_ROOT + path);
        if (file.exists()) {
            return true;
        }
        return false;
    }

    private byte[] readFileData(File file, int fileLength) throws IOException {
        FileInputStream fileIn = null;
        byte[] fileData = new byte[fileLength];
        try {
            fileIn = new FileInputStream(file);
            fileIn.read(fileData);
        } finally {
            if (fileIn != null)
                fileIn.close();
        }
        return fileData;
    }

    private String getContentType(String fileRequested) {
        String result = "text/plain";
        String what = fileRequested.split("\\.")[fileRequested.split("\\.").length - 1];
        switch (what) {
            case "html":
                result = "text/html; charset=UTF-8";
                break;
            case "js":
                result = "text/javascript; charset=UTF-8";
                break;
            case "css":
                result = "text/css; charset=UTF-8";
                break;
            case "png":
                result = "image/png";
                break;
            case "ico":
                result = "image/vnd.microsoft.icon; charset=UTF-8";
                break;
        }
        return result;
    }

    private void fileNotFound(Socket connect, BufferedOutputStream dataOut, PrintWriter out, String fileRequested, String method) throws IOException {
        webServer.notFound(connect, fileRequested, method);
        File file = new File(WEB_ROOT + webServer.getWebServer().notFound());
        String content = "text/html";
        print(file, new String(readFileData(file, (int) file.length())), content, dataOut, out, 404);
    }

    private void locked(Socket socket, String path, String method, BufferedOutputStream dataOut, PrintWriter out) {
        webServer.securityIssue(socket, path, method, 423);
        print(null, "Security issue detected, request denied", "text/html", dataOut, out, 423);
    }

}
