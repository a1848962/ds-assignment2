package com.weatheraggregation.server;

/*
* - Store all the weather data received from all content servers in a single JSON file
* - Weather data should be identifiable by the station ID that links the data to it's originating content server.
* - When the aggregation server receives multiple PUT requests from the same content server, only
*   the latest received data should be stored and old data from that server should be overwritten.
* - Start on port 4567 by default but accept a single command line argument providing a port number
* - Remove any items in the JSON from content servers that have not communicated for 30 seconds
* COMMUNICATION:
*   - First time weather data is received and the storage file is created, return status 201 - HTTP_CREATED
*   - If later uploads (updates) are successful, you should return status 200
*   - Any request other than GET or PUT should return status 400
*   - Sending no content to the server should return status 204
*   - Sending invalid JSON data (JSON does not make sense) should return status 500
* */

public class AggregationServer {

}
