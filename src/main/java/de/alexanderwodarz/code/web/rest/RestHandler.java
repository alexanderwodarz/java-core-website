package de.alexanderwodarz.code.web.rest;

import de.alexanderwodarz.code.log.Level;
import de.alexanderwodarz.code.log.Log;
import de.alexanderwodarz.code.web.WebCore;
import de.alexanderwodarz.code.web.rest.annotation.PathVariable;
import de.alexanderwodarz.code.web.rest.annotation.QueryParam;
import de.alexanderwodarz.code.web.rest.annotation.RequestBody;
import de.alexanderwodarz.code.web.rest.authentication.AuthenticationFilterResponse;
import de.alexanderwodarz.code.web.rest.authentication.AuthenticationManager;
import de.alexanderwodarz.code.web.rest.authentication.CorsResponse;
import lombok.SneakyThrows;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RestHandler extends Thread {

    private Socket connect;

    public RestHandler(Socket connect) {
        this.connect = connect;
    }

    public void run() {
        PrintWriter out = null;
        BufferedOutputStream dataOut = null;
        String fileRequested = null;
        RequestData data = new RequestData();
        HashMap<String, String> headers = new HashMap<>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(connect.getInputStream()));
            String line = br.readLine();
            boolean first = true;
            while (line.length() != 0) {
                if (first) {
                    data.setMethod(line.split(" ")[0]);
                    data.setPath(line.split(" ")[1]);
                    data.setScheme(line.split(" ")[2]);
                    first = false;
                }
                if (line.contains(":"))
                    headers.put(line.split(":")[0].toLowerCase(Locale.ROOT), line.split(":")[1].trim());
                line = br.readLine();
            }
            String body = "";
            while (br.ready())
                body += (char) br.read();
            if (headers.size() == 0) return;
            data.setHeaders(headers);
            data.setBody(body);
            data.setSocket(connect);
            if (data.getPath().contains("?")) {
                String queryString = data.getPath().split("\\?")[1];
                for (String query : queryString.split("&")) {
                    String[] split = query.split("=");
                    String key = split[0];
                    String value = split.length > 1 ? split[1] : "";
                    data.getQueries().put(key, value);
                }
                data.setPath(data.getPath().split("\\?")[0]);
            }
            out = new PrintWriter(connect.getOutputStream(), true, StandardCharsets.UTF_8);
            dataOut = new BufferedOutputStream(connect.getOutputStream());
            List<String> responseHeaders = new ArrayList<>();
            if (WebCore.getFilter() != null) {
                if (data.getMethod().equalsIgnoreCase("options")) {
                    Method method = Arrays.stream(WebCore.getFilter().getMethods()).filter(m -> m.getName().equalsIgnoreCase("doCors")).findFirst().orElse(null);
                    if (method != null) {
                        CorsResponse response = (CorsResponse) method.invoke(null, data);
                        print("", "", dataOut, out, 200, response.getHeaders());
                        return;
                    }
                }
                Method method = Arrays.stream(WebCore.getFilter().getMethods()).filter(m -> m.getName().equalsIgnoreCase("doFilter")).findFirst().orElse(null);
                if (method != null) {
                    AuthenticationFilterResponse response = (AuthenticationFilterResponse) method.invoke(null, data);
                    if (!response.isAccess()) {
                        print(response.getError().toString(), "application/json", dataOut, out, response.getCode());
                        return;
                    }
                }
                Method cors = Arrays.stream(WebCore.getFilter().getMethods()).filter(m -> m.getName().equalsIgnoreCase("doCors")).findFirst().orElse(null);
                if (cors != null) {
                    CorsResponse response = (CorsResponse) cors.invoke(null, data);
                    responseHeaders = response.getHeaders();
                }
            }
            RestWebRequest req = findRequest(data);
            if (req == null) {
                print("{\"error\":\"not found\"}", "application/json", dataOut, out, 404);
            } else {
                Object[] o = new Object[req.getMethod().getParameterCount()];
                for (int i = 0; i < req.getMethod().getParameters().length; i++) {
                    Parameter parameter = req.getMethod().getParameters()[i];
                    if (parameter.isAnnotationPresent(PathVariable.class)) {
                        PathVariable variable = parameter.getAnnotation(PathVariable.class);
                        o[i] = req.getFindPathResponse().getVariables().get("{" + variable.value() + "}");
                    }
                    if (parameter.isAnnotationPresent(QueryParam.class)) {
                        QueryParam param = parameter.getAnnotation(QueryParam.class);
                        String value = req.getFindPathResponse().getQueries().get(param.value());
                        Optional<String> test = Optional.of(value);
                        o[i] = test;
                    }
                    if (parameter.isAnnotationPresent(RequestBody.class)) {
                        Class<?> type = parameter.getType();
                        if (type.equals(JSONObject.class)) {
                            o[i] = new JSONObject(body);
                        } else if (type.equals(JSONArray.class)) {
                            o[i] = new JSONArray(body);
                        } else if (type.equals(Integer.class)) {
                            o[i] = Integer.parseInt(body);
                        } else if (type.equals(Double.class)) {
                            o[i] = Double.parseDouble(body);
                        } else if (type.equals(Long.class)) {
                            o[i] = Long.parseLong(body);
                        } else {
                            o[i] = body;
                        }
                    }
                    if (parameter.getType() == RequestData.class)
                        o[i] = data;
                }

                ResponseData response;
                if (req.getMethod().getParameterCount() > 0)
                    response = (ResponseData) req.getMethod().invoke(null, o);
                else
                    response = (ResponseData) req.getMethod().invoke(null);
                AuthenticationManager.setAuthentication(null);
                print(response.getBody(), req.getProduces(), dataOut, out, response.getCode(), responseHeaders);
            }
        }catch (JSONException | IllegalArgumentException e){
            print("{\"error\":\"invalid argument\"}", "application/json", dataOut, out, 400);
        } catch (InvocationTargetException e) {
            Log.log(e.getMessage(), Level.ERROR);
            data.setMethod("GET");
            data.setPath("/error/500");
            RestWebRequest req = findRequest(data);
            String body = "{\"error\":\"internal server error\"}";
            int code = 500;
            if (req != null) {
                ResponseData response = trigger(req, data);
                if (response != null) {
                    body = response.getBody();
                    code = response.getCode();
                }
            }
            print(body, "application/json", dataOut, out, code);
        } catch (FileNotFoundException fnfe) {
            try {
                fileNotFound(dataOut, out, fileRequested, "");
            } catch (IOException ioe) {
                fnfe.printStackTrace();
            }
        } catch (NullPointerException | IllegalAccessException e) {
        } catch (IOException ioe) {
            System.err.println("Server error : " + ioe);
        } finally {
            try {
                if (out != null)
                    out.close();
                if (dataOut != null)
                    dataOut.close();
                connect.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public ResponseData trigger(RestWebRequest req, RequestData data) {
        Object[] o = new Object[req.getMethod().getParameterCount()];
        for (int i = 0; i < req.getMethod().getParameters().length; i++) {
            Parameter parameter = req.getMethod().getParameters()[i];
            if (parameter.isAnnotationPresent(PathVariable.class)) {
                PathVariable variable = parameter.getAnnotation(PathVariable.class);
                o[i] = req.getFindPathResponse().getVariables().get("{" + variable.value() + "}");
            }
            if (parameter.getType() == RequestData.class)
                o[i] = data;
        }
        try {
            return (ResponseData) req.getMethod().invoke(null);
        } catch (IllegalAccessException | InvocationTargetException | NullPointerException ex) {
            return null;
        }
    }

    @SneakyThrows
    public RestWebRequest findRequest(RequestData data) {
        RestWebRequest req = null;
        String requestedPath = data.getPath();
        if (!requestedPath.endsWith("/")) requestedPath += "/";
        for (RestWebRequest request : WebCore.getRequests()) {
            if (!request.getRequest().method().equalsIgnoreCase(data.getMethod())) continue;
            String path = formatPath(request);
            FindPathResponse findPathResponse = testPath(requestedPath, path);
            if (!findPathResponse.isFound()) continue;
            findPathResponse.setQueries(data.getQueries());
            req = request;
            req.setFindPathResponse(findPathResponse);
            if (findPathResponse.isExactMatch()) break;
        }
        return req;
    }

    public FindPathResponse testPath(String requested, String toTest) {
        FindPathResponse response = new FindPathResponse();
        if (requested.split("/").length != toTest.split("/").length) return response;
        String withRegex = toTest.replaceAll("\\{.*?\\}", "(.*)");
        if (!requested.matches(withRegex)) return response;
        HashMap<Integer, String> variables = new HashMap<>();
        HashMap<String, String> entries = new HashMap<>();
        for (int i = 0; i < toTest.split("/").length; i++) {
            if (!toTest.split("/")[i].matches("\\{.*\\}")) continue;
            variables.put(i, toTest.split("/")[i]);
        }
        for (int i = 0; i < requested.split("/").length; i++) {
            if (!variables.containsKey(i)) continue;
            entries.put(variables.get(i), requested.split("/")[i]);
        }
        response.setFound(true);
        response.setVariables(entries);
        if (entries.size() == 0) response.setExactMatch(true);
        return response;
    }

    private void print(String content, String contentType, BufferedOutputStream dataOut, PrintWriter out, int status) {
        print(content, contentType, dataOut, out, status, null);
    }

    @SneakyThrows
    private void print(String content, String contentType, BufferedOutputStream dataOut, PrintWriter out, int status, List<String> headers) {
        int fileLength = 0;
        byte[] fileData = null;
        boolean image = false;
        if (contentType.contains("html") || contentType.contains("javascript")) {
            fileData = content.getBytes(StandardCharsets.UTF_8);
            fileLength = content.getBytes().length;
        } else if (contentType.equals("image/png")) {
            image = true;
        } else {
            fileData = content.getBytes(StandardCharsets.UTF_8);
            fileLength = fileData.length;
        }
        if (!contentType.toLowerCase().endsWith("; charset=utf-8"))
            contentType += "; charset=UTF-8";

        out.println("HTTP/1.1 " + status);
        out.println("Server: Java HTTP von Alex lol");
        out.println("Date: " + new Date());
        out.println("Content-type: " + contentType);
        out.println("Content-Length: " + fileLength);
        out.println("Connection: Keep-Alive");
        if (headers != null && headers.size() > 0) {
            for (String header : headers) {
                out.println(header);
            }
        }
        out.println();
        out.flush();

        try {
            dataOut.write(fileData, 0, fileLength);
            dataOut.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] readFileData(File file, int fileLength) throws IOException {
        FileInputStream fileIn = null;
        byte[] fileData = new byte[fileLength];
        try {
            fileIn = new FileInputStream(file);
            fileIn.read(fileData);
        } finally {
            if (fileIn != null) fileIn.close();
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

    private String formatPath(RestWebRequest request) {
        String result = "";
        if (request.getController().path().length() > 0) {
            if (!request.getController().path().startsWith("/")) result = "/";
            result += request.getController().path();
        }
        if (request.getRequest().path().length() > 0) {
            if (result.endsWith("/") && request.getRequest().path().startsWith("/"))
                result = result.substring(0, result.length() - 1);
            result += request.getRequest().path();
        }
        if (!result.endsWith("/")) result += "/";
        return result;
    }

    private void fileNotFound(BufferedOutputStream dataOut, PrintWriter out, String fileRequested, String method) throws IOException {
        String file = "404 Not found";
        String content = "text/html";
        print(file, content, dataOut, out, 404);
    }

    private void locked(Socket socket, String path, String method, BufferedOutputStream dataOut, PrintWriter out) {
        print("Security issue detected, request denied", "text/html", dataOut, out, 423);
    }

}
