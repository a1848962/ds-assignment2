package com.weatheraggregation.client;

/*
* - Read the command line to find the server name and port number (in URL format) and optionally a station ID
* - Send a GET request for the weather data
* - Strip returned data of JSON formatting and display, one line at a time, with the attribute and its value.
* - Possible formats for the server name and port number include:
*   - "http://servername.domain.domain:portnumber"
*   - "http://servername:portnumber" (with implicit domain information)
*   - "servername:portnumber" (with implicit domain and protocol information).
*
* */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weatheraggregation.utils.*;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;

public class GETClient {
    private static final int MAX_RETRY_COUNT = 3;
    private static int retryCount = 0;

    public static void main(String[] args) throws IOException {
        LamportClock clock = new LamportClock(); // initialise clock
        Scanner scanner = new Scanner(System.in);

        // get server address from args, or request if not provided.
        String serverAddress;
        if (args.length < 1) {
            System.out.println("Please enter aggregation server address in one of the following formats:");
            System.out.println(" - http://servername.domain.domain:portnumber");
            System.out.println(" - http://servername:portnumber");
            System.out.println(" - servername:portnumber");
            serverAddress = scanner.nextLine().trim();
        } else {
            serverAddress = args[0];
        }

        ServerData server = new ServerData(serverAddress); // parse arg into ServerData object to store connection information

        System.out.println("Usage:");
        System.out.println(" - 'all' to request all data");
        System.out.println(" - '[station ID]' to request a specific station");
        System.out.println(" - 'exit' to close client");

        // Loop to continuously listen for user input
        while (true) {
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Exiting...");
                break;
            }

            String stationID = input.equalsIgnoreCase("all") ? "" : input;

            // Increment clock before sending request
            clock.increment();

            // send GET request with lamport time
            String GET_REQUEST = "GET /weather/" + stationID + " HTTP/1.1\r\n" +
                    "Host: " + server.name + server.domain + "\r\n" +
                    "Lamport-Time: " + clock.getTime() + "\r\n\r\n";

            boolean success = false;
            Map<String, String> headers = new HashMap<>();
            int clientLamportTime = -1;
            BufferedReader socketIn = null;
            while (retryCount < MAX_RETRY_COUNT) {
                // establish connection to server through socket
                try (Socket socket = new Socket(server.name, server.port)) {
                    // create an OutputStream to write to socket out, and a BufferedReader to read from socket in
                    OutputStream socketOut = socket.getOutputStream();
                    socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    socketOut.write(GET_REQUEST.getBytes());
                    socketOut.flush();

                    String statusLine = socketIn.readLine();
                    System.out.println("Server response: " + statusLine);
                    headers = ParsingUtils.parseHeaders(socketIn);
                    clientLamportTime = Integer.parseInt(headers.get("Lamport-Time"));

                    String[] statusSplit = statusLine.split(" ");
                    if (statusSplit[1].startsWith("2")) {
                        // status code 2XX OK
                        success = true;
                        // close streams
                        socketOut.close();
                        break;
                    } else if (statusSplit[1].startsWith("5") || statusSplit[1].equals("404")) {
                        // status code 5XX Internal Server Error
                        // status code 404 Not Found
                        // retry
                        retryCount++;
                    } else {
                        // status code indicates client-side error
                        // close streams
                        socketOut.close();
                        break;
                    }

                    // close streams
                    socketOut.close();
                } catch (Exception ex) {
                    System.out.println("Server not found: " + ex.getMessage());
                    System.out.println("Please enter new connection details or press enter to try again:");
                    input = scanner.nextLine().trim();
                    if (input.equalsIgnoreCase("exit")) {
                        System.out.println("Exiting...");
                        return;
                    } else if (!input.isEmpty()) {
                        try {
                            server = new ServerData(input);
                        } catch (Exception serverException) {
                            System.out.println("Error parsing server data: " + serverException.getMessage());
                        }
                    }
                }
            }

            // if request was not successful, reset retryCount and skip this iteration
            if (!success) {
                System.out.println(retryCount == MAX_RETRY_COUNT ?
                        "Request failed, reached retry limit." :
                        "Server response indicates invalid request. Please try another request.");
                retryCount = 0;
                continue;
            }

            // TO-DO: handle lamport clock
            // parse remainder of socketIn buffer (payload) to JSON
            String[] jsonErrorCode = new String[2]; // string to hold error code
            ObjectNode weatherData = ParsingUtils.parseJSON(socketIn, jsonErrorCode, headers);

            if (weatherData != null) {
                // print formatted JSON weather data
                printWeatherData(weatherData);
            } else {
                // print clientside error message jsonErrorCode[1]
                System.out.println(jsonErrorCode[1]);
            }

            retryCount = 0;
        }
    }

    /* function to print JSON in a neat custom format */
    // this function was assisted by a LLM
    private static void printWeatherData(ObjectNode weatherData) {
        try {
            Iterator<Map.Entry<String, JsonNode>> fields = weatherData.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                ObjectNode stationData = (ObjectNode) entry.getValue();

                // iterate over the fields in stationData and print each key-value pair
                Iterator<Map.Entry<String, JsonNode>> stationFields = stationData.fields();
                Map.Entry<String, JsonNode> id = stationFields.next();
                System.out.println("## WEATHER DATA FOR " + id.getValue().asText() + " ##");

                while (stationFields.hasNext()) {
                    Map.Entry<String, JsonNode> field = stationFields.next();
                    System.out.println(field.getKey() + ": " + field.getValue().asText());
                }

                System.out.println();
            }
        } catch (Exception ex) {
            System.out.println("Error printing weather data: " + ex.getMessage());
        }
    }
}
