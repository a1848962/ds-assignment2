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
    private int retryCount = 0;
    private final LamportClock clock;
    private ServerData server;
    private final Scanner scanner;
    private boolean running = true;  // Flag to control the loop

    public GETClient(String serverAddress) {
        this.clock = new LamportClock();
        this.server = new ServerData(serverAddress);
        this.scanner = new Scanner(System.in);
    }

    // start client to listen for commands and send requests
    public void start() {
        System.out.println("Usage:");
        System.out.println(" - 'all' to request all data");
        System.out.println(" - '[station ID]' to request a specific station");
        System.out.println(" - 'exit' to close client");

        // loop while running flag is true
        while (running) {
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Exiting...");
                close();
                break;
            }

            // stationID is blank if input=all, otherwise =input
            String stationID = input.equalsIgnoreCase("all") ? "" : input;

            boolean success = sendGetRequest(stationID); // send request

            // check failure
            if (!success) {
                System.out.println(retryCount == MAX_RETRY_COUNT ?
                        "Request failed, reached retry limit." :
                        "Server response indicates invalid request. Please try another request.");
                retryCount = 0;  // Reset retry count after failure
            }
        }
    }

    // send GET request for weather data
    public boolean sendGetRequest(String stationID) {
        clock.increment(); // increment clock before sending request

        // send GET request with lamport time
        String GET_REQUEST = "GET /weather/" + stationID + " HTTP/1.1\r\n" +
                "Host: " + server.name + server.domain + "\r\n" +
                "Lamport-Time: " + clock.getTime() + "\r\n\r\n";

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
                try {
                    headers = ParsingUtils.parseHeaders(socketIn);
                } catch (IOException ex) {
                    System.out.println("Invalid headers: " + ex.getMessage());
                    break;
                }
                clientLamportTime = Integer.parseInt(headers.get("Lamport-Time"));

                String[] statusSplit = statusLine.split(" ");
                if (statusSplit[1].startsWith("2")) {
                    // status code 2XX OK
                    socketOut.close(); // close streams
                    retryCount = 0;  // reset retry count on success

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
                    return true; // success
                } else if (statusSplit[1].startsWith("5") || statusSplit[1].equals("404")) {
                    // status code 5XX Internal Server Error
                    // status code 404 Not Found
                    retryCount++; // retry
                } else {
                    // status code indicates client-side error
                    // close streams
                    socketOut.close();
                    break;
                }
            } catch (Exception ex) {
                handleConnectionError(ex);
            }
        }

        return false; // all retries failed
    }

    /* function to handle failure to connect to server and provide option to enter new server address */
    private void handleConnectionError(Exception ex) {
        System.out.println("Server not found: " + ex.getMessage());
        System.out.println("Please enter new connection details or press enter to try again:");
        String input = scanner.nextLine().trim();
        if (input.equalsIgnoreCase("exit")) {
            close();
        } else if (!input.isEmpty()) {
            try {
                server = new ServerData(input);
            } catch (Exception e) {
                System.out.println("Error parsing server data: " + e.getMessage());
            }
        }
    }

    // function to stop the server and clear resources
    public void close() {
        System.out.println("Shutting down client...");
        running = false;  // Stop the main loop
        scanner.close();
        System.out.println("Successfully shut down client");
        System.exit(0);
    }


    public static void main(String[] args) {
        String serverAddress;
        if (args.length < 1) {
            System.out.println("Please enter aggregation server address in one of the following formats:");
            System.out.println(" - http://servername.domain.domain:portnumber");
            System.out.println(" - http://servername:portnumber");
            System.out.println(" - servername:portnumber");
            Scanner scanner = new Scanner(System.in);
            serverAddress = scanner.nextLine().trim();
        } else {
            serverAddress = args[0];
        }

        GETClient client = new GETClient(serverAddress);
        client.start();
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
