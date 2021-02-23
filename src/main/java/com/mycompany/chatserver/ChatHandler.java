package com.mycompany.chatserver;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ChatHandler implements HttpHandler {

    public ChatHandler() {
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            handlePostRequest(exchange);

        } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            handleGetRequest(exchange);

        } else {
            handleBadRequest(exchange);
        }
    }

    private void handlePostRequest(HttpExchange exchange) throws IOException {
        // Handle POST request (client sent new chat message)

        String errorResponse = "";
        int code = 200;

        try {

            Headers headers = exchange.getRequestHeaders();
            int contentLength = 0;
            String contentType = "";

            if (headers.containsKey("Content-length")) {
                contentLength = Integer.valueOf(headers.get("Content-Length").get(0));
            } else {
                errorResponse = "Content-length not defined";
                code = 411;
            }

            if (headers.containsKey("Content-Type")) {
                contentType = headers.get("Content-Type").get(0);
            } else {
                errorResponse = "No Content-Type specified in request";
                code = 400;
            }

            if (contentType.equalsIgnoreCase("application/json")) {

                InputStream stream = exchange.getRequestBody();

                String text = new BufferedReader(new InputStreamReader(stream,
                        StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));

                stream.close();

                JSONObject chatMessage = new JSONObject(text);

                String dateStr = chatMessage.getString("sent");
                OffsetDateTime odt = OffsetDateTime.parse(dateStr);

                LocalDateTime sent = odt.toLocalDateTime();
                String userName = chatMessage.get("user").toString();
                String message = chatMessage.getString("message");

                ChatMessage newMessage = new ChatMessage(sent, userName, message);

                if (!text.isEmpty()) {
                    //Add message to database
                    ChatDatabase db = ChatDatabase.getInstance();
                    db.insertMessage(message, sent, userName);

                    exchange.sendResponseHeaders(200, -1);
                } else {
                    errorResponse = "Text was empty.";
                    code = 400;
                }

            } else if (!contentType.isEmpty() && !contentType.equalsIgnoreCase("application/json")) {
                errorResponse = "Content-Type must be application/json";
                code = 411;
            }

        } catch (JSONException e) {
            code = 400;
            errorResponse = "Invalid JSON-file";
        }

        if (code < 200 || code > 299) {

            byte[] bytes = errorResponse.getBytes("UTF-8");

            exchange.sendResponseHeaders(code, bytes.length);

            OutputStream os = exchange.getResponseBody();
            os.write(errorResponse.getBytes("UTF-8"));
            os.flush();
            os.close();
        }

        exchange.close();
    }

    private void handleGetRequest(HttpExchange exchange) throws IOException {
        // Handle GET request (client wants to see messages)

        try {
            ChatDatabase db = ChatDatabase.getInstance();

            Headers headers = exchange.getRequestHeaders();

            String lastModified = null;
            LocalDateTime fromWhichDate = null;
            long messagesSince = -1;

            if (headers.containsKey("If-Modified-Since")) {

                lastModified = headers.get("If-Modified-Since").get(0);

                ZonedDateTime zd = ZonedDateTime.parse(lastModified);
                fromWhichDate = zd.toLocalDateTime();

                messagesSince = fromWhichDate.toInstant(ZoneOffset.UTC).toEpochMilli();
            } else {
                System.out.println("No last-modified header found");
            }

            //Formatter for timestamps
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");

            ArrayList<ChatMessage> dbMessages = db.getMessages(messagesSince);

            if (dbMessages.isEmpty()) {
                exchange.sendResponseHeaders(204, -1);
            } else {

                //Sort messages by timestamp
                Collections.sort(dbMessages, (ChatMessage lhs, ChatMessage rhs) -> lhs.sent.compareTo(rhs.sent));

                //Create JSONArray to add messages to
                JSONArray responseMessages = new JSONArray();

                LocalDateTime latest = null;

                for (ChatMessage message : dbMessages) {

                    //Keep track of latest message in db
                    if (latest == null || message.sent.isAfter(latest)) {
                        latest = message.sent;
                    }

                    //Format timestamps
                    ZonedDateTime zonedDateTime = message.sent.atZone(ZoneId.of("UTC"));
                    String formattedTimestamp = zonedDateTime.format(formatter);

                    //Create new JSONObject with message details
                    JSONObject json = new JSONObject();

                    json.put("user", message.userName);
                    json.put("message", message.message);
                    json.put("sent", formattedTimestamp);

                    //Add JSONObject to JSONArray
                    responseMessages.put(json);
                }

                if (latest != null) {
                    ZonedDateTime zonedDateTime = latest.atZone(ZoneId.of("UTC"));
                    String latestFormatted = zonedDateTime.format(formatter);

                    //Add last-modified header with value of latest msg timestamp
                    exchange.getResponseHeaders().add("Last-Modified", latestFormatted);
                }

                String JSON = responseMessages.toString();
                byte[] bytes = JSON.getBytes("UTF-8");

                exchange.sendResponseHeaders(200, bytes.length);

                OutputStream os = exchange.getResponseBody();
                os.write(bytes);

                os.flush();
                os.close();
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

        exchange.close();
    }

    private void handleBadRequest(HttpExchange exchange) throws IOException {
        // Handle error if request not GET or POST
        String errorResponse = "Not supported";

        byte[] bytes = errorResponse.getBytes("UTF-8");

        exchange.sendResponseHeaders(400, bytes.length);

        OutputStream os = exchange.getResponseBody();

        os.write(errorResponse.getBytes("UTF-8"));
        os.flush();
        os.close();

        exchange.close();
    }
}
