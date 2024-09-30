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

import com.weatheraggregation.utils.ServerData;
import com.weatheraggregation.utils.LamportClock;

import java.io.*;
import java.net.*;

public class GETClient {
    public static void main(String[] args) {
        if (args.length != 1) {
            // check args
            throw new IllegalArgumentException("Expected one argument specifying connection information.");
        }

        LamportClock clock = new LamportClock(); // initialise clock

        // parse arg into ServerData object to store connection information
        ServerData server = new ServerData(args[0]);

        // establish connection to server through socket
        try (Socket socket = new Socket(server.name, server.port)) {
            // create an OutputStream to write to socket out, and a BufferedReader to read from socket in
            OutputStream socketOut = socket.getOutputStream();
            BufferedReader socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // increment clock before sending request
            clock.increment();

            // send GET request with lamport time
            String GET_REQUEST = "GET /data HTTP/1.1\r\n" +
                                 "Host: " + server.name + server.domain + "\r\n" +
                                 "Lamport-Time: " + clock.getTime() + "\r\n\r\n";
            socketOut.write(GET_REQUEST.getBytes());

            // read response (JSON HANDLING NEEDS WORK)
            String line = socketIn.readLine();
            StringBuilder jsonResponse = new StringBuilder();
            int serverTimestamp = -1;
            while (line != null) {
                System.out.println(line);

                if (line.startsWith("Lamport-Time:")) {
                    // get server timestamp
                    serverTimestamp = Integer.parseInt(line.split(":")[1]);
                }

                jsonResponse.append(line);

                // read next line
                line = socketIn.readLine();
            }

            // update client Lamport clock
            clock.update(serverTimestamp);

            // close streams
            socketIn.close();
            socketOut.close();
            socket.close();

            System.out.println("Received JSON: " + jsonResponse.toString());
            System.out.println("Updated Client Lamport Time: " + clock.getTime());

        } catch (UnknownHostException ex) {
            System.out.println("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("I/O error: " + ex.getMessage());
        }
    }
}
