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
    private static final int MAX_STATIONS = 20; // do not hold data for more than 20 stations
    private static final String STATION_ID_STORAGE = "station_ids";

    // store weather data in a concurrent hashmap. Concurrent hashmap is used over the
    // traditional hashmap as it is optimised for multithreaded use.
    private static final Map<String, ObjectNode> weatherDataMap = new ConcurrentHashMap<>();
    private static final Map<String, Long> timestamps = new ConcurrentHashMap<>();
    private static final Set<String> stations = new HashSet<>();
    private static final ReentrantLock lock = new ReentrantLock();

    private static final LamportClock clock = new LamportClock(); // initialise clock

    public static void main(String[] args) throws IOException {
        // check for alternative port provided as argument, otherwise use default
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        // read data from persistent storage if present
        readLocalWD();

        // start listening on socket
        try (ServerSocket socket = new ServerSocket(port)) {
            System.out.println("Aggregation server running on port " + port);
            System.out.println("'exit' to shut down server and retain all weather data.");
            System.out.println("'exit -r' to shut down server and remove all weather data.");

            // TO-DO: implement exit commands
            while (true) {
                Socket clientSocket = socket.accept();
                // accept connection and fork to handle
                new Thread(new ConnectionHandler(clientSocket)).start();
            }
        } catch (IOException ex) {
            System.out.println("I/O error: " + ex.getMessage());
        }
    }

    // function to remove any stations exceeding EXPIRY_TIME
    private static void removeExpiredStations() {
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

        // loop through expired stations to update class variables and delete local files
        for (String stationID : expiredStations) {
            // remove from program memory
            weatherDataMap.remove(stationID);
            timestamps.remove(stationID);
            stations.remove(stationID);

            // remove from persistent memory
            File file = new File(stationID);
            if (!(file.exists() && file.delete())) {
                System.out.println("Error deleting expired local data for station " + stationID);
            }
        }

        updateStationsFile();
    }

    // function to remove oldest station if MAX_STATIONS is exceeded
    private static void removeExcessStations() {
        // This function is called after every PUT, so weatherDataMap.size() should
        // never be more than 1 over MAX_STATIONS. Therefore, only the oldest station
        // needs to be removed.
        if (stations.size() > MAX_STATIONS) {
            Long oldestTimestamp = Long.MAX_VALUE; // hold the smallest timestamp (oldest station)
            String oldestStationID = ""; // hold the smallest timestamp (oldest station)
            for (String stationID : weatherDataMap.keySet()) {
                Long stationTimestamp = timestamps.get(stationID);
                if (stationTimestamp != null && stationTimestamp < oldestTimestamp) {
                    oldestTimestamp = stationTimestamp;
                    oldestStationID = stationID;
                }
            }

            // remove from program memory
            weatherDataMap.remove(oldestStationID);
            timestamps.remove(oldestStationID);
            stations.remove(oldestStationID);

            // remove from persistent memory
            File file = new File(oldestStationID);
            if (!(file.exists() && file.delete())) {
                System.out.println("Error deleting expired local data for station " + oldestStationID);
            }

            updateStationsFile();
        }
    }

    /* function to update persistent storage of stations set */
    private static void updateStationsFile() {
        File stationIDFile = new File(STATION_ID_STORAGE);
        try (FileWriter writer = new FileWriter(stationIDFile)) {
            for (String id : stations) {
                writer.write(id + System.lineSeparator());
            }
        } catch (IOException ex) {
            System.out.println("Error updating station ID file: " + ex.getMessage());
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
                // write stationID timestamp to first line
                fileWriter.write(timestamps.get(stationID).toString() + System.lineSeparator());
                // write JSON object to rest of file
                mapper.writeValue(fileWriter, weatherData);
            } catch (IOException ex) {
                System.out.println("Error writing to local file for station " + stationID + ": " + ex.getMessage());
            }
        } else {
            System.out.println("Error: no weather data found for station: " + stationID);
        }

        updateStationsFile();
    }

    /* function to overwrite stationID data in map with contents of local file */
    // this function was written with the assistance of AI
    private static void readLocalWD() {
        File stationIDFile = new File(STATION_ID_STORAGE);

        // read stationIDs from STATION_ID_STORAGE
        if (stationIDFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(stationIDFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stations.add(line.trim());  // Add each station ID to the set
                }
            } catch (IOException ex) {
                System.out.println("Error reading station ID file: " + ex.getMessage());
            }
        } else {
            System.out.println("No local station ID file found.");
        }

        // read each file corresponding to stationID, populate weatherDataMap and update timestamps
        ObjectMapper mapper = new ObjectMapper();
        for (String stationID : stations) {
            File stationFile = new File(stationID);
            if (stationFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(stationFile))) {
                    // first line is timestamp
                    String timestamp = reader.readLine();
                    if (timestamp != null) {
                        Long stationTimestamp = Long.parseLong(timestamp.trim());
                        timestamps.put(stationID, stationTimestamp);
                    }

                    // rest of file is JSON weather data
                    ObjectNode weatherData = (ObjectNode) mapper.readTree(reader);
                    weatherDataMap.put(stationID, weatherData);
                } catch (IOException ex) {
                    System.out.println("Error reading weather data file for station " + stationID + ": " + ex.getMessage());
                }
            } else {
                System.out.println("Warning: Weather data file for station " + stationID + " not found.");
            }
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
            // check requested resource is /weather and isolate stationID if provided
            String[] resourceParts = resource.split("/");
            String stationID = ""; // default if no station is specified
            if (!resourceParts[1].equals("weather")) {
                // send 404 error code and exit
                returnErrorCode("404 Not Found", socketOut);
                return;
            } else if (resourceParts.length == 3) {
                stationID = resourceParts[2];
            }

            // read headers and update lamport time
            int clientLamportTime = parseHeaders(socketIn);
            lock.lock();
            boolean isCurrentRequest = clock.update(clientLamportTime);
            lock.unlock();

            // confirm lamport-time was sent in request header
            if (clientLamportTime < 0) {
                returnErrorCode("400 Bad Request", socketOut);
                return;
            }

            // remove expired data before building response
            lock.lock();
            removeExpiredStations();
            lock.unlock();

            try {
                ObjectNode responseData; // initialise JSON object to contain response
                if (stationID.isEmpty()) {
                    // no station ID specified, return all weather data stored in the AS
                    responseData = new ObjectMapper().createObjectNode();
                    weatherDataMap.forEach(responseData::set); // this line was provided by a LLM
                    // deliberately not locking around forEach call, it is fine for other threads to modify the map
                    // while building responseData. Removed/added stations will be reflected in responseData.
                } else {
                    // station ID provided, retrieve corresponding weather data
                    // concurrent hashmap does not require locking for atomic actions
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
                lock.lock();
                clock.increment();
                lock.unlock();

                // send response with payload
                socketOut.println("HTTP/1.1 200 OK");
                socketOut.println("Content-Type: application/json");
                socketOut.println("Content-Length: " + responseJson.length());
                socketOut.println("Lamport-Time: " + clock.getTime());
                socketOut.println();
                socketOut.println(responseJson);

            } catch (IOException ex) {
                System.out.println("Error handling GET request: " + ex.getMessage());
            }
        }

        private void handlePut(BufferedReader socketIn, PrintWriter socketOut, String resource) {
            // confirm resource follows format "/weather/stationID"
            String[] resourceParts = resource.split("/");
            if (resourceParts.length != 3 || !resourceParts[1].trim().equals("weather")) {
                // send 404 error code for invalid resource
                returnErrorCode("404 Not Found", socketOut);
                return;
            }

            String stationID = resourceParts[2].trim();

            // read headers and update lamport time
            int clientLamportTime = parseHeaders(socketIn);

            lock.lock();
            boolean isCurrentRequest = clock.update(clientLamportTime);
            lock.unlock();

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

            // parse payload to JSON object and store in server memory / persistent storage
            try {
                ObjectNode weatherData = (ObjectNode) new ObjectMapper().readTree(jsonContent.toString());

                boolean isNewStation = !weatherDataMap.containsKey(stationID);
                // boolean isNewStation = !stations.contains(stationID);

                // update data if lamport time is current (clientTime >= server clock)
                if (isCurrentRequest) {
                    // concurrent hashmap does not require locking for atomic actions
                    weatherDataMap.put(stationID, weatherData);
                    timestamps.put(stationID, System.currentTimeMillis());
                    stations.add(stationID);

                    // lock to update persistent storage and remove expired/excess stations
                    lock.lock();
                    writeLocalWD(stationID);
                    removeExpiredStations(); // remove expired stations BEFORE removing excess
                    removeExcessStations();
                    lock.unlock();
                }

                // increment clock before sending response
                lock.lock();
                clock.increment();
                lock.unlock();

                // send response (201 for new station, 200 for update)
                socketOut.println(isNewStation ? "HTTP/1.1 201 Created" : "HTTP/1.1 200 OK");
                socketOut.println("Content-Type: application/json");
                socketOut.println("Content-Length: 0");
                socketOut.println();
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
