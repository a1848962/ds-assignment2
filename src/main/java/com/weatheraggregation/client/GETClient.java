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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weatheraggregation.utils.*;

import java.io.*;
import java.net.*;
import java.util.Map;

public class GETClient {
    public static void main(String[] args) {
        String stationID = "";
        if (args.length == 2) {
            stationID = args[1];
        } else if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Expected one argument specifying connection information, optionally followed by a station ID");
        }

        LamportClock clock = new LamportClock(); // initialise clock

        // parse arg into ServerData object to store connection information
        ServerData server = new ServerData(args[0]);

        // increment clock before sending request
        clock.increment();

        // send GET request with lamport time
        String GET_REQUEST = "GET /weather/" + stationID + " HTTP/1.1\r\n" +
                "Host: " + server.name + server.domain + "\r\n" +
                "Lamport-Time: " + clock.getTime() + "\r\n\r\n";

        // establish connection to server through socket
        try (Socket socket = new Socket(server.name, server.port)) {
            // create an OutputStream to write to socket out, and a BufferedReader to read from socket in
            OutputStream socketOut = socket.getOutputStream();
            BufferedReader socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            socketOut.write(GET_REQUEST.getBytes());
            socketOut.flush();

            Map<String, String> headers = ParsingUtils.parseHeaders(socketIn);
            int clientLamportTime = Integer.parseInt(headers.get("Lamport-Time"));

            // TO-DO: handle lamport clock

            // parse remainder of socketIn buffer (payload) to JSON
            String[] jsonErrorCode = new String[2]; // string to hold error code
            ObjectNode weatherData = ParsingUtils.parseJSON(socketIn, jsonErrorCode);

            if (weatherData != null) {
                System.out.println("Received JSON: " + weatherData.toString());
            } else {
                // print clientside error message jsonErrorCode[1]
                System.out.println(jsonErrorCode[1]);
            }

            // close streams
            socketIn.close();
            socketOut.close();
        } catch (UnknownHostException ex) {
            System.out.println("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("I/O error: " + ex.getMessage());
        }
    }
}
