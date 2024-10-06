package com.weatheraggregation.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.Socket;
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
                headers.put(lineSplit[0].trim(), lineSplit[1].trim());
                line = socketIn.readLine();
            }
        } catch (IOException ex) {
            System.out.println("Error reading headers: " + ex.getMessage());
        }
        return headers;
    }

    /* Function to read socketIn to a string, then parse string to JSON.
     * errorCode[0] holds server error message, errorCode[1] holds client error message. */
    public static ObjectNode parseJSON(BufferedReader socketIn, String[] errorCode) {
        // read request payload to string
        StringBuilder jsonContent = new StringBuilder();
        String line;
        try {
            line = socketIn.readLine();
            while (line != null && !line.isEmpty()) {
                jsonContent.append(line);
                line = socketIn.readLine();
            }
        } catch (IOException ex) {
            errorCode[0] = "500 Internal Server Error";
            errorCode[1] = "Error reading payload: " + ex.getMessage();
            return null;
        }

        // check for empty payload
        if (jsonContent.isEmpty()) {
            errorCode[0] = "204 No Content";
            errorCode[1] = "Empty payload";
            return null;
        }

        // try parse string to JSON
        try {
            return (ObjectNode) new ObjectMapper().readTree(jsonContent.toString());
        } catch (Exception ex) {
            errorCode[0] = "500 Internal Server Error";
            errorCode[1] = "Error parsing payload to JSON: " + ex.getMessage();
            return null;
        }
    }
}