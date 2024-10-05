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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.weatheraggregation.utils.LamportClock;
import com.weatheraggregation.utils.ServerData;

public class ContentServer {
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

    public static void main(String[] args) {
        if (args.length != 2) {
            // check args
            throw new IllegalArgumentException("Expected two arguments: connection information and data filename.");
        }

        LamportClock clock = new LamportClock(); // initialise clock

        // parse arg into ServerData object to store connection information
        ServerData server = new ServerData(args[0]);

        // read weather data from file and parse to JSON
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode weatherData = mapper.createObjectNode();
        parseFileJSON(weatherData, args[0]);

        // get stationID from JSON
        JsonNode stationID = weatherData.get("id");

        // increment clock before sending request
        clock.increment();

        // build the PUT request from the connection data and the weather data
        String weatherDataString = weatherData.toString();
        String PUT_REQUEST = "PUT /weather/" + stationID.asText() + " HTTP/1.1\r\n" +
                "Host: " + server.name + server.domain + "\r\n" +
                "User-Agent: ATOMClient/1/0\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + weatherDataString.getBytes().length + "\r\n" +
                "Lamport-Time: " + clock.getTime() + "\r\n\r\n" +
                weatherDataString + "\r\n"; // JSON body

        // establish connection to server through socket
        try (Socket socket = new Socket(server.name, server.port)) {
            // create an OutputStream to write to socket out, and a BufferedReader to read from socket in
            OutputStream socketOut = socket.getOutputStream();
            BufferedReader socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // send PUT request with lamport time
            socketOut.write(PUT_REQUEST.getBytes());
            socketOut.flush();

            // TO-DO: Read response

        } catch (UnknownHostException ex) {
            System.out.println("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("I/O error: " + ex.getMessage());
        }
    }
}
