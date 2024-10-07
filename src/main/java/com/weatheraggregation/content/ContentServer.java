package com.weatheraggregation.content;

/*
* Read two parameters from the command line:
* 1. server name and port number (as for GET)
* 2. location of a file in the file system local to the Content Server (expected to be in your project folder).
*    The file will contain weather data to be assembled into JSON format and then uploaded to the server.
*
* EXAMPLE PUT MESSAGE:

PUT /weather.json HTTP/1.1
User-Agent: ATOMClient/1/0
Content-Type: (You should work this one out)
Content-Length: (And this one too)
{
    "id" : "IDS60901",
    ... (DATA HERE) ...
    "wind_spd_kt": 8
}

* Use an existing JSON parser
* Implement this assignment using Sockets rather than HttpServer
*
* */

import java.io.*;

import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.weatheraggregation.utils.LamportClock;
import com.weatheraggregation.utils.ServerData;

public class ContentServer {
    private static final int MAX_RETRY_COUNT = 3;
    private static int retryCount = 0;

    public static void main(String[] args) {
        if (args.length != 2) {
            // check args
            throw new IllegalArgumentException("Expected two arguments: connection information and data filename.");
        }

        LamportClock clock = new LamportClock(); // initialise clock
        Scanner scanner = new Scanner(System.in); // scanner to read inputs from command line
        ServerData server = new ServerData(args[0]); // parse arg into ServerData object to store connection information

        System.out.println("Usage:");
        System.out.println(" - 'update' to resend weather data");
        System.out.println(" - 'exit' to close content server");

        while (true) {
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Exiting...");
                break;
            } else if (!input.equalsIgnoreCase("update")) {
                System.out.println("Usage:");
                System.out.println(" - 'update' to resend weather data");
                System.out.println(" - 'exit' to close content server");
                continue;
            }

            // read weather data from file and parse to JSON
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode weatherData = mapper.createObjectNode();
            parseFileJSON(weatherData, args[1]);

            // get stationID from JSON
            JsonNode stationID = weatherData.get("id");

            // build the PUT request from the connection data and the weather data
            String weatherDataString = weatherData.toString();
            String PUT_REQUEST = "PUT /weather/" + stationID.asText() + " HTTP/1.1\r\n" +
                    "Host: " + server.name + server.domain + "\r\n" +
                    "User-Agent: ATOMClient/1/0\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + weatherDataString.getBytes().length + "\r\n" +
                    "Lamport-Time: " + clock.getTime() + "\r\n\r\n" +
                    weatherDataString + "\r\n"; // JSON body

            // increment clock before sending request
            clock.increment();

            boolean success = false;
            while (retryCount < MAX_RETRY_COUNT) {
                // establish connection to server through socket
                try (Socket socket = new Socket(server.name, server.port)) {
                    // create an OutputStream to write to socket out, and a BufferedReader to read from socket in
                    OutputStream socketOut = socket.getOutputStream();
                    BufferedReader socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    System.out.println("Sending weather data for station " + stationID.asText() + "...");
                    // send PUT request with lamport time
                    socketOut.write(PUT_REQUEST.getBytes());
                    socketOut.flush();

                    /*   - First time weather data is received and the storage file is created, return status 201 - HTTP_CREATED
                     *   - If later uploads (updates) are successful, you should return status 200
                     *   - Any request other than GET or PUT should return status 400
                     *   - Sending no content to the server should return status 204
                     *   - Sending invalid JSON data (JSON does not make sense) should return status 500
                     */

                    String statusLine = socketIn.readLine();
                    System.out.println("Server Response: " + statusLine);
                    String[] statusSplit = statusLine.split(" ");

                    if (statusSplit[1].startsWith("5")) {
                        System.out.println("Invalid JSON or internal server error, retrying...");
                        retryCount++;
                    } else if (statusSplit[1].equals("204")) {
                        System.out.println("Server received PUT request with no payload, retrying...");
                        retryCount++;
                    } else if (statusSplit[1].startsWith("2")) {
                        // 200 OK, break loop
                        socketOut.close();
                        success = true;
                        retryCount = 0;
                        break;
                    } else {
                        // status code indicates client-side error
                        // close streams
                        System.out.println("Server response indicates invalid request. Please try another request.");
                        socketOut.close();
                        break;
                    }
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
            if (!success) {
                System.out.println(retryCount == MAX_RETRY_COUNT ?
                        "Request failed, reached retry limit." :
                        "Request failed after " + retryCount + " attempts.");
                retryCount = 0;
            }
        }
    }

    /* FUNCTION TO PARSE DATA FROM FILE TO A JSON OBJECT */
    public static void parseFileJSON(ObjectNode weatherData, String fileName) {
        // read file using a BufferedReader object with a 1KB buffer
        // https://www.baeldung.com/java-buffered-reader
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName), 1024)) {
            String line;
            // read line until reader buffer is empty
            while ((line = reader.readLine()) != null) {
                // split each line at : char
                String[] keyValuePair = line.split(":", 2);

                if (keyValuePair.length == 2) {
                    // remove any leading/trailing whitespace:
                    String key = keyValuePair[0].trim();
                    String value = keyValuePair[1].trim();

                    // add key/value pair to weatherData object:
                    try {
                        // try parsing value as number
                        if (value.contains(".")) {
                            weatherData.put(key, Double.parseDouble(value));
                        } else {
                            weatherData.put(key, Integer.parseInt(value));
                        }
                    } catch (NumberFormatException e) {
                        // if exception is thrown then parse as string
                        weatherData.put(key, value);
                    }
                }
            }
        } catch (FileNotFoundException ex) {
            System.out.println("File not found: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("I/O error: " + ex.getMessage());
        }
    }
}
