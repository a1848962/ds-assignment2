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
*   - Question71: A GET request without a station ID should return all weather data stored in the AS
* */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.weatheraggregation.utils.LamportClock;

public class AggregationServer {
    private static final int DEFAULT_PORT = 4567;
    private static final int EXPIRY_TIME = 30000; // 30 seconds
    private static final String WD_FILE = "weather_data.json";

    // store weather data in a concurrent hashmap. Concurrent hashmap is used over the
    // traditional hashmap as it is optimised for multithreaded use.
    private static final Map<String, ObjectNode> weatherDataMap = new ConcurrentHashMap<>();
    private static final Map<String, Long> timestamps = new ConcurrentHashMap<>();
    private static final ReentrantLock lock = new ReentrantLock();

    private static final LamportClock clock = new LamportClock(); // initialise clock

    public static void main(String[] args) throws IOException {
        // check for alternative port provided as argument, otherwise use default
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        try (ServerSocket socket = new ServerSocket(port)) {
            System.out.println("Aggregation server running on port " + port);

            // start listening on socket
            while (true) {
                Socket clientSocket = socket.accept();
                // accept connection and fork to handle
                new Thread(new ConnectionHandler(clientSocket)).start();
            }
        } catch (IOException ex) {
            System.out.println("I/O error: " + ex.getMessage());
        }
    }

    private static void removeExpiredWD() {
        // use a set to store stationIDs of expired data
        Set<String> expiredStations = new HashSet<>();

        // expiry checking and deletion loops are seperated to avoid a
        // ConcurrentModificationException caused by modifying a map while
        // iterating over it

        // loop through weatherDataMap keyset and check timestamps
        long currentTime = System.currentTimeMillis();
        for (String stationID : weatherDataMap.keySet()) {
            Long stationTimestamp = timestamps.get(stationID);
            if (stationTimestamp != null && (currentTime - stationTimestamp > EXPIRY_TIME)) {
                expiredStations.add(stationID); // add stationID to expired set
            }
        }

        // loop through expired stations and remove old data
        for (String stationID : expiredStations) {
            // remove data from `weatherDataMap` and `timestamps` hashmaps
            weatherDataMap.remove(stationID);
            timestamps.remove(stationID);

            // remove local WD file
            File file = new File(stationID);
            if (!(file.exists() && file.delete())) {
                System.out.println("Error deleting expired local data for station " + stationID);
            }
        }
    }

    /* function to overwrite local weather data file for provided station with current weatherDataMap */
    private static void writeLocalWD(String stationID) {
        // retrieve station weatherdata from map
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode weatherData = weatherDataMap.get(stationID);

        if (weatherData != null) {
            // open file for writing, file name is stationID
            File file = new File(stationID);
            try (FileWriter fileWriter = new FileWriter(file)) {
                mapper.writeValue(fileWriter, weatherData);
            } catch (IOException ex) {
                System.out.println("Error writing to local file for station " + stationID + ": " + ex.getMessage());
            }
        } else {
            System.out.println("Error: no weather data found for station: " + stationID);
        }
    }


    /* function to overwrite stationID data in map with contents of local file */
    // this function was written with the assistance of AI
    private static void readLocalWD(String stationID) {
        ObjectMapper mapper = new ObjectMapper();
        // open file with name stationID and confirm it exists
        File file = new File(stationID);
        if (file.exists()) {
            try {
                // read weatherdata from file and cast to JSON object
                ObjectNode weatherData = (ObjectNode) mapper.readTree(file);
                weatherDataMap.put(stationID, weatherData);
            } catch (IOException ex) {
                System.out.println("Error reading local file for station " + stationID + ": " + ex.getMessage());
            }
        } else {
            System.out.println("Error: local file not found for station: " + stationID);
        }
    }

    private static class ConnectionHandler implements Runnable {
        private final Socket socket;

        public ConnectionHandler(Socket clientSocket) {
            this.socket = clientSocket;
        }

        // override run to handle new connections
        @Override
        public void run() {
            try {
                // before handling each new connection, immediately check for expired data
                // idea for improvement: run this in a background thread
                removeExpiredWD();

                // use a printwriter for writing to socket, and a bufferedreader for reading
                PrintWriter socketOut = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Confirm first line of request is one of the following and handle accordingly
                // "GET /weather/station ..."
                // "PUT /weather/station ..."
                String firstHeader = socketIn.readLine();
                if (firstHeader != null) {
                    String[] request = firstHeader.split(" ");

                    if (request[0].equals("GET")) {
                        handleGet(socketIn, socketOut, request[1]);
                    } else if (request[0].equals("PUT")) {
                        handlePut(socketIn, socketOut, request[1]);
                    } else {
                        // send 400 error code
                        returnErrorCode("400 Bad Request", socketOut);
                    }
                }
            } catch (UnknownHostException ex) {
                System.out.println("Server not found: " + ex.getMessage());
            } catch (IOException ex) {
                System.out.println("I/O error: " + ex.getMessage());
            }
        }

        private void handleGet(BufferedReader socketIn, PrintWriter socketOut, String resource) {
            // check requested resource is weather and isolate stationID if provided
            String[] resourceParts = resource.split("/");
            String stationID = "any"; // default if no station is specified
            if (!resourceParts[1].equals("weather")) {
                // send 404 error code and exit
                returnErrorCode("404 Not Found", socketOut);
                return;
            } else if (resourceParts.length == 3) {
                stationID = resourceParts[2];
            }

            // read headers and update lamport time
            int clientLamportTime = parseHeaders(socketIn);
            boolean isCurrentRequest = clock.update(clientLamportTime);

            // confirm lamport-time was sent in request header
            if (clientLamportTime < 0) {
                returnErrorCode("400 Bad Request", socketOut);
                return;
            }

            // acquire lock and retrieve weather data
            boolean isLockAcquired = false;
            try {
                isLockAcquired = lock.tryLock(1, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                System.out.println("Error obtaining lock: " + ex.getMessage());
            }
            if (isLockAcquired) {
                try {
                    ObjectNode responseData; // initialise JSON object to contain response
                    if (stationID.equals("any")) {
                        // no station ID specified, return all weather data stored in the AS
                        responseData = new ObjectMapper().createObjectNode();
                        weatherDataMap.forEach(responseData::set); // this line was provided by a LLM
                    } else {
                        // station ID provided, retrieve corresponding weather data
                        responseData = weatherDataMap.get(stationID);
                        // if station ID not in map, return 404 error
                        if (responseData == null) {
                            returnErrorCode("404 Not Found", socketOut);
                            return;
                        }
                    }

                    // parse response data to JSON string
                    String responseJson = new ObjectMapper().writeValueAsString(responseData);

                    // increment clock before sending response
                    clock.increment();

                    // send response with payload
                    socketOut.println("HTTP/1.1 200 OK");
                    socketOut.println("Content-Type: application/json");
                    socketOut.println("Content-Length: " + responseJson.length());
                    socketOut.println("Lamport-Time: " + clock.getTime());
                    socketOut.println();
                    socketOut.println(responseJson);

                } catch (IOException ex) {
                    System.out.println("Error handling GET request: " + ex.getMessage());
                } finally {
                    lock.unlock();
                }
            } else {
                // failed to acquire lock, return error
                System.out.println("Error obtaining lock in GET handler");
                returnErrorCode("500 Internal Server Error", socketOut);
            }
        }

        private void handlePut(BufferedReader socketIn, PrintWriter socketOut, String resource) {
            String[] resourceParts = resource.split("/");
            if (resourceParts.length != 3 || !resourceParts[1].equals("weather")) {
                // send 404 error code for invalid resource
                returnErrorCode("404 Not Found", socketOut);
                return;
            }

            String stationID = resourceParts[2];

            // read headers and update lamport time
            int clientLamportTime = parseHeaders(socketIn);
            boolean isCurrentRequest = clock.update(clientLamportTime);

            // confirm lamport-time was sent in request header
            if (clientLamportTime < 0) {
                returnErrorCode("400 Bad Request", socketOut);
                return;
            }

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
                System.out.println("Error reading payload: " + ex.getMessage());
            }

            // send 204 error if no payload is provided
            if (jsonContent.isEmpty()) {
                returnErrorCode("204 No Content", socketOut);
                return;
            }

            // parse payload to JSON object
            try {
                ObjectNode weatherData = (ObjectNode) new ObjectMapper().readTree(jsonContent.toString());

                boolean isNewStation = !weatherDataMap.containsKey(stationID);

                // acquire lock and add weather data to hashmap and local WD file
                boolean isLockAcquired = false;
                try {
                    isLockAcquired = lock.tryLock(1, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    System.out.println("Error obtaining lock: " + ex.getMessage());
                }
                if (isLockAcquired) {
                    boolean dataUpdated = false;
                    try {
                        // update data if lamport time is current (clientTime >= server clock)
                        if (isCurrentRequest) {
                            weatherDataMap.put(stationID, weatherData);
                            timestamps.put(stationID, System.currentTimeMillis());
                            dataUpdated = true;
                        }
                    } finally {
                        lock.unlock();
                    }

                    // write data to local file after lock is released
                    if (dataUpdated) {
                        writeLocalWD(stationID);
                    }

                    // increment clock before sending response
                    clock.increment();

                    // send response (201 for new station, 200 for update)
                    socketOut.println(isNewStation ? "HTTP/1.1 201 Created" : "HTTP/1.1 200 OK");
                    socketOut.println("Content-Type: application/json");
                    socketOut.println("Content-Length: 0");
                    socketOut.println();
                } else {
                    // failed to acquire lock, return error
                    System.out.println("Error obtaining lock in PUT handler");
                    returnErrorCode("500 Internal Server Error", socketOut);
                }
            } catch (Exception ex) {
                // invalid JSON, send 500 Internal Server Error
                returnErrorCode("500 Internal Server Error", socketOut);
            }
        }

        private int parseHeaders(BufferedReader socketIn) {
            int clientTime = -1;
            try {
                String line = socketIn.readLine();
                while (line != null && !line.isEmpty()) {
                    if (line.startsWith("Lamport-Time:")) {
                        // get client timestamp and update local clock with greatest time
                        clientTime = Integer.parseInt(line.split(":")[1].trim());
                    }
                    line = socketIn.readLine();
                }
            } catch (IOException ex) {
                System.out.println("Error reading headers: " + ex.getMessage());
            }
            return clientTime;
        }

        private void returnErrorCode(String errorCode, PrintWriter socketOut) {
            socketOut.println("HTTP/1.1 " + errorCode);
            socketOut.println("Content-Type: text/plain");
            socketOut.println("Content-Length: 0");
            socketOut.println("Lamport-Time: " + clock.getTime());
            socketOut.println();
        }
    }
}
