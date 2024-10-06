package com.weatheraggregation.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ParsingUtils {
    /* Function to read from socketIn buffer until empty line (signalling start of
     * payload) or end of message is reached. Returns headers parsed into a hashmap.
     * Note: assumes request/status line has already been read. */
    public static Map<String, String> parseHeaders(BufferedReader socketIn) {
        Map<String, String> headers = new HashMap<>();
        try {
            String line = socketIn.readLine();
            while (line != null && !line.isEmpty()) {
                String[] lineSplit = line.split(":");
                if (lineSplit.length == 2) {
                    headers.put(lineSplit[0].trim(), lineSplit[1].trim());
                } else {
                    throw new IOException("Invalid header line: " + line);
                }
                line = socketIn.readLine();
            }
        } catch (IOException ex) {
            System.out.println("Error reading headers: " + ex.getMessage());
        }
        return headers;
    }

    /* Function to read socketIn to a string, then parse string to JSON.
     * errorCode[0] holds server error message, errorCode[1] holds client error message. */
    public static ObjectNode parseJSON(BufferedReader socketIn, String[] errorCode, Map<String, String> headers) {
        int contentLength = Integer.parseInt(headers.get("Content-Length"));

        // check content length is not 0
        if (contentLength < 1) {
            errorCode[0] = "204 No Content";
            errorCode[1] = "Empty payload";
            return null;
        }

        // check Content-Type is application/json
        if (!headers.get("Content-Type").equals("application/json")) {
            errorCode[0] = "400 Bad Request";
            errorCode[1] = "Content-Type is not application/json";
            return null;
        }

        char[] buffer = new char[contentLength];
        try {
            // read exactly 'contentLength' characters from the socket
            int bytesRead = socketIn.read(buffer, 0, contentLength);
            if (bytesRead != contentLength) {
                errorCode[0] = "400 Bad Request";
                errorCode[1] = "Incomplete payload received";
                return null;
            }

            String jsonContent = new String(buffer);

            // Try to parse the string into a JSON ObjectNode
            return (ObjectNode) new ObjectMapper().readTree(jsonContent);
        } catch (Exception ex) {
            errorCode[0] = "500 Internal Server Error";
            errorCode[1] = "Error parsing payload to JSON: " + ex.getMessage();
            return null;
        }
    }
}