package de.alexanderwodarz.code.web.rest;

import de.alexanderwodarz.code.log.Level;
import de.alexanderwodarz.code.log.Log;
import de.alexanderwodarz.code.web.WebCore;
import de.alexanderwodarz.code.web.rest.annotation.PathVariable;
import de.alexanderwodarz.code.web.rest.annotation.QueryParam;
import de.alexanderwodarz.code.web.rest.annotation.RequestBody;
import de.alexanderwodarz.code.web.rest.authentication.AuthenticationFilterResponse;
import de.alexanderwodarz.code.web.rest.authentication.CorsResponse;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.json.HTTP.CRLF;

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
        data.setDate(System.currentTimeMillis());
        HashMap<String, String> headers = new HashMap<>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(connect.getInputStream()));
            String line = br.readLine();
            if (line == null)
                return;
            boolean first = true;
            while (line.length() != 0) {
                if (first) {
                    data.setMethod(line.split(" ")[0]);
                    data.setPath(line.split(" ")[1]);
                    data.setScheme(line.split(" ")[2]);
                    first = false;
                }
                if (line.contains(":"))
                    headers.put(line.split(": ")[0].toLowerCase(Locale.ROOT), line.split(": ")[1].trim());
                line = br.readLine();
            }
            String body = "";
            while (br.ready())
                body += (char) br.read();
            if (headers.size() == 0) return;
            data.setHeaders(headers);
            if (data.getHeader("cookie") != null)
                Arrays.stream(data.getHeader("cookie").split("; ")).sequential().forEach(cookie -> {
                    String[] splitted = cookie.split("=");
                    data.getCookies().add(new Cookie(splitted[0], splitted.length == 1 ? "" : splitted[1]));
                });
            data.setBody(body);
            data.setSocket(connect);
            if (data.getPath().contains("?")) {
                String[] queries = data.getPath().split("\\?");
                if (queries.length > 1) {
                    String queryString = data.getPath().split("\\?")[1];
                    for (String query : queryString.split("&")) {
                        String[] split = query.split("=");
                        String key = split[0];
                        String value = split.length > 1 ? split[1] : "";
                        data.getQueries().put(key, value);
                    }
                    data.setPath(data.getPath().split("\\?")[0]);
                }
            }
            out = new PrintWriter(connect.getOutputStream(), true, StandardCharsets.UTF_8);
            dataOut = new BufferedOutputStream(connect.getOutputStream());
            List<String> responseHeaders = new ArrayList<>();
            RestWebRequest req = findRequest(data);


            if (WebCore.getFilter() != null) {
                if (data.getMethod().equalsIgnoreCase("options")) {
                    Method method = Arrays.stream(WebCore.getFilter().getMethods()).filter(m -> m.getName().equalsIgnoreCase("doCors")).findFirst().orElse(null);
                    if (method != null) {
                        CorsResponse response = (CorsResponse) method.invoke(null, data);
                        print("", "", dataOut, out, 200, response.getHeaders());
                        return;
                    }
                }
                if (req == null) {
                    error(404, new JSONObject().put("error", "not found").put("path", data.getPath()).put("method", data.getMethod()).toString(), null, data, dataOut, out, false);
                    return;
                }
                data.setLevel(req.getRequest().level());
                data.setOriginalPath(req.getController().path() + req.getRequest().path());
                data.setVariables(req.getFindPathResponse().getVariables());
                Method method = Arrays.stream(WebCore.getFilter().getMethods()).filter(m -> m.getName().equalsIgnoreCase("doFilter")).findFirst().orElse(null);
                if (method != null) {
                    AuthenticationFilterResponse response = (AuthenticationFilterResponse) method.invoke(null, data);
                    if (response.getAuthentication() != null)
                        data.setAuthentication(response.getAuthentication());
                    if (!response.isAccess()) {
                        Method cors = Arrays.stream(WebCore.getFilter().getMethods()).filter(m -> m.getName().equalsIgnoreCase("doCors")).findFirst().orElse(null);
                        if (cors != null)
                            responseHeaders = ((CorsResponse) cors.invoke(null, data)).getHeaders();
                        print(response.getError().toString(), "application/json", dataOut, out, response.getCode(), responseHeaders);
                        return;
                    }
                }
                Method cors = Arrays.stream(WebCore.getFilter().getMethods()).filter(m -> m.getName().equalsIgnoreCase("doCors")).findFirst().orElse(null);
                if (cors != null) {
                    CorsResponse response = (CorsResponse) cors.invoke(null, data);
                    responseHeaders = response.getHeaders();
                }
            }
            if (req == null) {
                error(404, new JSONObject().put("error", "not found").put("path", data.getPath()).put("method", data.getMethod()).toString(), null, data, dataOut, out, false);
                return;
            }
            data.setLevel(req.getRequest().level());
            data.setOriginalPath(req.getController().path() + req.getRequest().path());
            data.setVariables(req.getFindPathResponse().getVariables());
            Object[] o = new Object[req.getMethod().getParameterCount()];
            for (int i = 0; i < req.getMethod().getParameters().length; i++) {
                Parameter parameter = req.getMethod().getParameters()[i];
                if (parameter.isAnnotationPresent(PathVariable.class)) {
                    PathVariable variable = parameter.getAnnotation(PathVariable.class);
                    String var = req.getFindPathResponse().getVariables().get("{" + variable.value() + "}");
                    if (parameter.getType().equals(Optional.class)) {
                        o[i] = Optional.of(var);
                    } else {
                        o[i] = var;
                    }
                }
                if (parameter.isAnnotationPresent(QueryParam.class)) {
                    QueryParam param = parameter.getAnnotation(QueryParam.class);
                    String value = req.getFindPathResponse().getQueries().get(param.value());
                    if (value == null)
                        o[i] = Optional.empty();
                    else
                        o[i] = Optional.of(value);
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
                    } else if (BodyModel.class.isAssignableFrom(type)) {
                        try {
                            JSONObject object = new JSONObject(body);
                            BodyModel instance = (BodyModel) type.getDeclaredConstructor().newInstance();
                            if (!setFields(instance, object, type, data, dataOut, out))
                                return;
                            o[i] = instance;
                        } catch (Exception e) {
                            if (e instanceof JSONException && e.getMessage().startsWith("Unterminated string at")) {
                                error(400, new JSONObject().put("error", "invalid request body").toString(), e, data, dataOut, out);
                                return;
                            }
                            if (e instanceof JSONException && e.getMessage().contains("JSONObject[") && e.getMessage().contains("not found")) {
                                error(400, new JSONObject().put("error", "missing parameter " + e.getMessage().split("\"")[1]).toString(), e, data, dataOut, out);
                                return;
                            }
                            o[i] = null;
                            e.printStackTrace();
                        }
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
            responseHeaders.addAll(response.getHeaders());
            if (response.getFile() == null)
                print(response.getBody(), req.getProduces(), dataOut, out, response.getCode(), responseHeaders);
            else
                printFile(response.getFile(), req.getProduces(), dataOut, out, response.getCode(), responseHeaders);
            log(data, response);
        } catch (JSONException | IllegalArgumentException e) {
            error(400, new JSONObject().put("error", "invalid argument").toString(), e, data, dataOut, out);
        } catch (InvocationTargetException e) {
            error(500, new JSONObject().put("error", "internal server error").toString(), e, data, dataOut, out);
        } catch (FileNotFoundException | NullPointerException | IllegalAccessException e) {
            error(404, new JSONObject().put("error", "not found").put("method", data.getMethod()).put("path", data.getPath()).toString(), e, data, dataOut, out);
        } catch (ArrayIndexOutOfBoundsException e) {
            error(400, new JSONObject().put("error", "invalid request").toString(), e, data, dataOut, out);
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
            }

        }
    }

    public void error(int statusCode, String body, Exception e, RequestData data, BufferedOutputStream dataOut, PrintWriter out) {
        error(statusCode, body, e, data, dataOut, out, true);
    }

    public void error(int statusCode, String body, Exception e, RequestData data, BufferedOutputStream dataOut, PrintWriter out, boolean showError) {
        if (showError)
            Log.log(e.getMessage(), Level.ERROR);
        data.setOriginalMethod(data.getMethod());
        data.setMethod("GET");
        data.setPath("/error/" + statusCode);
        RestWebRequest req = findRequest(data);

        if (req != null) {
            data.setException(e);
            ResponseData response = trigger(req, data);
            if (response != null) {
                body = response.getBody();
                statusCode = response.getCode();
            }
        }
        print(body, "application/json", dataOut, out, statusCode);
        ResponseData responseData = new ResponseData(body, statusCode);
        log(data, responseData);
    }

    private void log(RequestData request, ResponseData response) {
        if (WebCore.collection == null)
            return;
        Document logging = new Document();
        Document requestLogging = new Document();
        requestLogging.append("method", request.getMethod());
        requestLogging.append("path", request.getPath());
        requestLogging.append("originalMethod", request.getOriginalMethod());
        requestLogging.append("originalPath", request.getOriginalPath());
        requestLogging.append("body", request.getBody());
        Document requestHeader = new Document();
        for (Map.Entry<String, String> stringStringEntry : request.getHeaders().entrySet())
            requestHeader.append(stringStringEntry.getKey(), stringStringEntry.getValue());
        Document variables = new Document();
        for (Map.Entry<String, String> stringStringEntry : request.getVariables().entrySet())
            variables.append(stringStringEntry.getKey(), stringStringEntry.getValue());
        Document queries = new Document();
        for (Map.Entry<String, String> stringStringEntry : request.getQueries().entrySet())
            queries.append(stringStringEntry.getKey(), stringStringEntry.getValue());
        requestLogging.append("queries", queries);
        requestLogging.append("date", request.getDate());
        requestLogging.append("variable", variables);
        requestLogging.append("headers", requestHeader);
        logging.append("request", requestLogging);
        if (response != null) {
            Document responseLogging = new Document();
            Document responseHeader = new Document();
            response.getHeaders().stream().forEach(h -> {
                String[] split = h.split(":");
                responseHeader.append(split[0], split.length == 1 ? "" : split[1]);
            });
            responseLogging.append("header", responseHeader);
            responseLogging.append("status", response.getCode());
            responseLogging.append("body", response.getBody());
            logging.append("response", responseLogging);
        }
        WebCore.collection.insertOne(logging);
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
            return (ResponseData) req.getMethod().invoke(null, o);
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

    private boolean setFields(BodyModel instance, JSONObject object, Class<?> type, RequestData data, BufferedOutputStream dataOut, PrintWriter out) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException, InstantiationException {
        Field[] fields = type.getDeclaredFields();
        instance.setObj(object);
        for (Field field : fields) {
            if (!object.has(field.getName())) {
                error(400, new JSONObject().put("error", "missing parameter " + field.getName()).toString(), new Exception("missing parameter " + field.getName()), data, dataOut, out);
                return false;
            }
            field.setAccessible(true);
            if (BodyModel.class.isAssignableFrom(field.getType()) && object.get(field.getName()) instanceof JSONObject) {
                BodyModel newInstance = (BodyModel) field.getType().getDeclaredConstructor().newInstance();
                if (!setFields(newInstance, object.getJSONObject(field.getName()), field.getType(), data, dataOut, out))
                    return false;
                field.set(instance, newInstance);
                continue;
            }
            switch (field.getType().getName()) {
                case "java.util.List": {
                    if (object.get(field.getName()) instanceof JSONArray) {
                        JSONArray jsonArray = object.getJSONArray(field.getName());
                        List<Object> list = new ArrayList<>();
                        Class<?> componentType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                        for (int i = 0; i < jsonArray.length(); i++) {
                            if (jsonArray.get(i) instanceof String) {
                                list.add(jsonArray.get(i));
                                continue;
                            }
                            JSONObject jsonItem = jsonArray.getJSONObject(i);
                            if (BodyModel.class.isAssignableFrom(componentType) && jsonItem != null) {
                                BodyModel newInstance = (BodyModel) componentType.getDeclaredConstructor().newInstance();
                                if (!setFields(newInstance, jsonItem, componentType, data, dataOut, out))
                                    return false;
                                list.add(newInstance);
                            }
                        }
                        field.set(instance, list);
                    } else
                        error(400, new JSONObject().put("error", field.getName() + " must be JSONArray").toString(), new Exception(field.getName() + " must be JSONArray"), data, dataOut, out);
                    break;
                }
                case "java.lang.String": {
                    if (object.get(field.getName()) instanceof String)
                        field.set(instance, object.get(field.getName()));
                    else
                        error(400, new JSONObject().put("error", field.getName() + " must be String").toString(), new Exception(field.getName() + " must be String"), data, dataOut, out);
                    break;
                }
                case "org.json.JSONObject": {
                    if (object.get(field.getName()) instanceof JSONObject)
                        field.set(instance, object.get(field.getName()));
                    else
                        error(400, new JSONObject().put("error", field.getName() + " must be JSONObject").toString(), new Exception(field.getName() + " must be JSONObject"), data, dataOut, out);
                    break;
                }
                case "org.json.JSONArray": {
                    if (object.get(field.getName()) instanceof JSONArray)
                        field.set(instance, object.get(field.getName()));
                    else
                        error(400, new JSONObject().put("error", field.getName() + " must be JSONArray").toString(), new Exception(field.getName() + " must be JSONArray"), data, dataOut, out);
                    break;
                }
                case "boolean": {
                    if (object.get(field.getName()) instanceof Boolean)
                        field.set(instance, object.get(field.getName()));
                    else
                        error(400, new JSONObject().put("error", field.getName() + " must be boolean").toString(), new Exception(field.getName() + " must be boolean"), data, dataOut, out);
                    break;
                }
                case "int": {
                    if (object.get(field.getName()) instanceof Integer)
                        field.set(instance, object.get(field.getName()));
                    else
                        error(400, new JSONObject().put("error", field.getName() + " must be integer").toString(), new Exception(field.getName() + " must be integer"), data, dataOut, out);
                    break;
                }
                case "long": {
                    if (object.get(field.getName()) instanceof Long || object.get(field.getName()) instanceof Integer)
                        field.set(instance, object.get(field.getName()));
                    else
                        error(400, new JSONObject().put("error", field.getName() + " must be long").toString(), new Exception(field.getName() + " must be long"), data, dataOut, out);
                    break;
                }
                case "double": {
                    if (object.get(field.getName()) instanceof Double || object.get(field.getName()) instanceof Integer)
                        field.set(instance, object.get(field.getName()));
                    else
                        error(400, new JSONObject().put("error", field.getName() + " must be double").toString(), new Exception(field.getName() + " must be double"), data, dataOut, out);
                    break;
                }
                case "float": {
                    if (object.get(field.getName()) instanceof Float || object.get(field.getName()) instanceof Integer)
                        field.set(instance, object.get(field.getName()));
                    else
                        error(400, new JSONObject().put("error", field.getName() + " must be float").toString(), new Exception(field.getName() + " must be float"), data, dataOut, out);
                    break;
                }
            }
        }
        return true;
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
        if (out == null)
            return;
        int fileLength = 0;
        byte[] fileData = null;
        if (contentType.contains("html") || contentType.contains("javascript")) {
            fileData = content.getBytes(StandardCharsets.UTF_8);
            fileLength = content.getBytes().length;
        } else {
            fileData = content.getBytes(StandardCharsets.UTF_8);
            fileLength = fileData.length;
        }
        if (!contentType.toLowerCase().endsWith("; charset=utf-8"))
            contentType += "; charset=UTF-8";

        out.println("HTTP/1.1 " + status);
        out.println("Server: java-webcore");
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
        }
    }

    @SneakyThrows
    private void printFile(File file, String contentType, BufferedOutputStream dataOut, PrintWriter out, int status, List<String> headers) {
        InputStream is = new FileInputStream(file);
        byte[] bytes = IOUtils.toByteArray(is);
        out.println("HTTP/1.1 " + status);
        out.println("Server: java-webcore");
        out.println("Date: " + new Date());
        out.println("Content-type: " + contentType);
        out.println("Content-Length: " + bytes.length);
        out.println("Connection: Keep-Alive");
        if (headers != null && headers.size() > 0) {
            for (String header : headers) {
                out.println(header);
            }
        }
        out.println();
        out.flush();
        dataOut.write(bytes);
        dataOut.write((CRLF + CRLF).getBytes());
        dataOut.flush();
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
