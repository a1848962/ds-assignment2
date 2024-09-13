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

public class GETClient {
    public static void main(String[] args) {
        if (args.length != 1) {
            // check args
            throw new IllegalArgumentException("Exactly one argument is required.");
        }

        ServerData serverData = ServerData.parseServerData(args[0]);

        System.out.println("Protocol: " + serverData.protocol);
        System.out.println("Name: " + serverData.name);
        System.out.println("Domain: " + serverData.domain);
        System.out.println("Port: " + serverData.port);

    }
}
