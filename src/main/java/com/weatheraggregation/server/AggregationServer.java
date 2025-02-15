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
import com.weatheraggregation.utils.ParsingUtils;

public class AggregationServer {
    private static final boolean LIVE_UPDATES = true;
    private static final int DEFAULT_PORT = 4567;
    private static final int EXPIRY_TIME = 30 * 1000; // 30 seconds
    private static final int MAX_STATIONS = 20; // do not hold data for more than 20 stations
    private static final String STATION_ID_STORAGE = "station_ids";

    // store weather data in a concurrent hashmap. Concurrent hashmap is used over the
    // traditional hashmap as it is optimised for multithreaded use.
    final Map<String, ObjectNode> weatherDataMap = new ConcurrentHashMap<>();
    private final Map<String, Long> timestamps = new ConcurrentHashMap<>();
    private final Set<String> stations = new HashSet<>();
    private final ReentrantLock lock = new ReentrantLock();

    private final LamportClock clock = new LamportClock(); // initialise clock

    private final int port;
    private ServerSocket serverSocket;

    public AggregationServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        // create server on default port unless one is provided
        AggregationServer server = new AggregationServer(port);

        try {
            server.start(); // start server
        } catch (IOException ex) {
            System.out.println("Error starting server: " + ex.getMessage());
        }
    }

    public void start() throws IOException {
        readLocalWD(); // read data from persistent storage

        serverSocket = new ServerSocket(port); // create socket

        System.out.println("Aggregation server running on port " + port);
        System.out.println("Usage:");
        System.out.println("'exit' to shut down server and retain all weather data.");
        System.out.println("'exit -r' to shut down server and remove all weather data.");
        listenForExit(); // fork thread to listen for exit commands on stdin

        // start listening on socket
        while (!serverSocket.isClosed()) {
            Socket clientSocket = serverSocket.accept();
            new Thread(new ConnectionHandler(clientSocket)).start(); // Handle client in a new thread
        }
    }

    /* function to listen on stdin for exit command, and shut down server if received) */
    private void listenForExit() {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String input;
                while ((input = reader.readLine()) != null) {
                    if (input.trim().equals("exit")) {
                        close(false);  // exit and retain weather data
                        break;
                    } else if (input.trim().equals("exit -r")) {
                        close(true);  // exit and remove weather data
                        break;
                    } else {
                        System.out.println("Invalid command: " + input);
                        System.out.println("Usage:");
                        System.out.println("'exit' to shut down server and retain all weather data.");
                        System.out.println("'exit -r' to shut down server and remove all weather data.");
                    }
                }
            } catch (IOException ex) {
                System.out.println("Error reading shutdown commands: " + ex.getMessage());
            }
        }).start();
    }

    /* function to close server. Will remove WD in persistent storage according to removeData bool */
    void close(boolean removeData) {
        System.out.println("Shutting down...");

        if (removeData) {
            System.out.println("Removing all persistent weather data from local storage...");
            // delete stationIDs file
            File stationIDFile = new File(STATION_ID_STORAGE);
            if (stationIDFile.exists() && !stationIDFile.delete()) {
                System.out.println("Error deleting station ID file");
            }

            // delete all local station files
            for (String stationID : stations) {
                File stationFile = new File(stationID);
                if (stationFile.exists() && !stationFile.delete()) {
                    System.out.println("Error deleting local file for station " + stationID);
                }
            }
        } else {
            System.out.println("Weather data will be retained");
        }

        // close server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ex) {
            System.out.println("Error closing server socket: " + ex.getMessage());
        }

        System.out.println("Server shutdown complete");
        System.exit(0);
    }

    // function to remove any stations exceeding EXPIRY_TIME
    void removeExpiredStations() {
        if (LIVE_UPDATES) {System.out.println("Removing expired stations...");}

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
    private void removeExcessStations() {
        // This function is called after every PUT, so weatherDataMap.size() should
        // never be more than 1 over MAX_STATIONS. Therefore, only the oldest station
        // needs to be removed.
        if (LIVE_UPDATES) {System.out.println("Removing excess stations...");}

        if (stations.size() > MAX_STATIONS) {
            long oldestTimestamp = Long.MAX_VALUE; // hold the smallest timestamp (oldest station)
            String oldestStationID = ""; // hold the smallest timestamp (oldest station)
            for (String stationID : weatherDataMap.keySet()) {
                // use Long wrapper to allow direct assignment of null value
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
    private void updateStationsFile() {
        if (LIVE_UPDATES) {System.out.println("Updating stations file...");}
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
    private void writeLocalWD(String stationID) {
        if (LIVE_UPDATES) {System.out.println("Writing local data for station " + stationID + "...");}

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
    private void readLocalWD() {
        if (LIVE_UPDATES) {System.out.println("Reading local data...");}

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

        // remove expired data before handling request
        lock.lock();
        try {
            removeExpiredStations();
        } finally {
            lock.unlock();
        }
    }

    private class ConnectionHandler implements Runnable {
        private final Socket socket;

        public ConnectionHandler(Socket clientSocket) {
            this.socket = clientSocket;
        }

        // override run to handle new connections
        @Override
        public void run() {
            if (LIVE_UPDATES) {System.out.println("New connection accepted...");}
            // use a outputsteam for writing to socket, and a bufferedreader for reading
            // declare outside of try block to allow socket closing in finally block
            OutputStream socketOut = null;
            BufferedReader socketIn = null;
            try {
                socketOut = socket.getOutputStream();
                socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Confirm first line of request is one of the following and handle accordingly
                // "GET /weather/station ..."
                // "PUT /weather/station ..."
                String requestLine = socketIn.readLine();
                if (requestLine != null) {
                    String[] request = requestLine.split(" ");

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
            } finally {
                // close socket and streams
                try {
                    if (socketIn != null) socketIn.close();
                    if (socketOut != null) socketOut.close();
                    if (socket != null) socket.close();
                } catch (IOException ex) {
                    System.out.println("Error closing socket: " + ex.getMessage());
                }
            }
        }

        private void returnErrorCode(String errorCode, OutputStream socketOut) {
            if (LIVE_UPDATES) {System.out.println("Returning error code " + errorCode + "...");}

            String RESPONSE =
                    "HTTP/1.1 " + errorCode + "\r\n" +
                    "Content-Type: text/plain" + "\r\n" +
                    "Content-Length: 0" + "\r\n" +
                    "Lamport-Time: " + clock.getTime() + "\r\n\r\n";

            try {
                socketOut.write(RESPONSE.getBytes());
                socketOut.flush();
            } catch (IOException e) {
                System.out.println("Error writing to socket...");
            }
        }

        private void handleGet(BufferedReader socketIn, OutputStream socketOut, String resource) {
            if (LIVE_UPDATES) {System.out.println("Handling GET request...");}

            // remove expired data before building response
            lock.lock();
            try {
                removeExpiredStations();
            } finally {
                lock.unlock();
            }

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

            // parse headers and update lamport time
            Map<String, String> headers;
            try {
                headers = ParsingUtils.parseHeaders(socketIn);
            } catch (IOException ex) {
                System.out.println("Error reading headers: " + ex.getMessage());
                returnErrorCode("400 Bad Request", socketOut);
                return;
            }
            int clientLamportTime = Integer.parseInt(headers.get("Lamport-Time"));

            // confirm lamport-time was sent in request header
            if (clientLamportTime < 0) {
                returnErrorCode("400 Bad Request", socketOut);
                return;
            }

            lock.lock();
            try {
                boolean clientIsAhead = clock.update(clientLamportTime);
            } finally {
                lock.unlock();
            }

            if (stations.isEmpty()) {
                returnErrorCode("404 Not Found", socketOut);
                return;
            }

            try {
                ObjectNode responseData = new ObjectMapper().createObjectNode(); // initialise JSON object to contain response
                if (stationID.isEmpty()) {
                    // no station ID specified, return all weather data stored in the AS
                    weatherDataMap.forEach(responseData::set); // this line was provided by a LLM
                    // deliberately not locking around forEach call, it is fine for other threads to modify the map
                    // while building responseData. Removed/added stations will be reflected in responseData.
                } else {
                    // station ID provided, retrieve corresponding weather data
                    // concurrent hashmap does not require locking for atomic actions
                    ObjectNode stationData = weatherDataMap.get(stationID);
                    // if station ID not in map, return 404 error
                    if (stationData == null) {
                        if (LIVE_UPDATES) {System.out.println("Station " + stationID + " not found.");}
                        returnErrorCode("404 Not Found", socketOut);
                        return;
                    }
                    responseData.set(stationID, stationData); // wrap response in set for parsing at client
                }

                // parse response data to JSON string
                String responseJson = new ObjectMapper().writeValueAsString(responseData);

                // increment clock before sending response
                lock.lock();
                try {
                    clock.increment();
                } finally {
                    lock.unlock();
                }

                if (LIVE_UPDATES) {System.out.println("GET request successfully handled, sending response...");}

                // send response with payload
                String RESPONSE =
                        "HTTP/1.1 200 OK" + "\r\n" +
                        "Content-Type: application/json" + "\r\n" +
                        "Content-Length: " + responseJson.length() + "\r\n" +
                        "Lamport-Time: " + clock.getTime() + "\r\n\r\n" +
                        responseJson;

                try {
                    socketOut.write(RESPONSE.getBytes());
                    socketOut.flush();
                } catch (IOException e) {
                    System.out.println("Error writing to socket...");
                }
            } catch (IOException ex) {
                System.out.println("Error handling GET request: " + ex.getMessage());
            }
        }

        private void handlePut(BufferedReader socketIn, OutputStream socketOut, String resource) {
            if (LIVE_UPDATES) {System.out.println("Handling PUT request...");}

            // confirm resource follows format "/weather/stationID"
            String[] resourceParts = resource.split("/");
            if (resourceParts.length != 3 || !resourceParts[1].trim().equals("weather")) {
                // send 404 error code for invalid resource
                returnErrorCode("404 Not Found", socketOut);
                return;
            }

            String stationID = resourceParts[2].trim();

            // read headers and update lamport time
            // parse headers and update lamport time
            Map<String, String> headers;
            try {
                headers = ParsingUtils.parseHeaders(socketIn);
            } catch (IOException ex) {
                System.out.println("Error reading headers: " + ex.getMessage());
                returnErrorCode("400 Bad Request", socketOut);
                return;
            }

            int clientLamportTime = Integer.parseInt(headers.get("Lamport-Time"));

            // confirm lamport-time was sent in request header
            if (clientLamportTime < 0) {
                returnErrorCode("400 Bad Request", socketOut);
                return;
            }

            if (LIVE_UPDATES) {System.out.println("Headers parsed successfully, locking to update clock");}

            lock.lock();
            try {
                boolean clientIsAhead = clock.update(clientLamportTime);
            } finally {
                lock.unlock();
            }

            if (LIVE_UPDATES) {System.out.println("Parsing JSON data");}
            // parse remainder of socketIn buffer (payload) to JSON
            String[] jsonErrorCode = new String[2]; // string to hold error code
            ObjectNode weatherData = ParsingUtils.parseJSON(socketIn, jsonErrorCode, headers);
            if (weatherData == null) {
                if (LIVE_UPDATES) {System.out.println("JSON parsing returned null");}
                returnErrorCode(jsonErrorCode[0], socketOut);
                return;
            }

            if (LIVE_UPDATES) {System.out.println("JSON Parsed successfully");}

            boolean isNewStation = !weatherDataMap.containsKey(stationID);
            // boolean isNewStation = !stations.contains(stationID);

            // update data:
            // concurrent hashmap does not require locking for atomic actions
            weatherDataMap.put(stationID, weatherData);
            timestamps.put(stationID, System.currentTimeMillis());
            stations.add(stationID);

            // lock to update persistent storage and remove expired/excess stations
            lock.lock();
            try {
                writeLocalWD(stationID);
                removeExpiredStations(); // remove expired stations BEFORE removing excess
                removeExcessStations();
                clock.increment(); // increment clock before sending response
            } finally {
                lock.unlock();
            }

            if (LIVE_UPDATES) {System.out.println("PUT request successfully handled, sending response...");}

            // send response (201 for new station, 200 for update)
            String RESPONSE =
                    (isNewStation ? "HTTP/1.1 201 Created" : "HTTP/1.1 200 OK") + "\r\n" +
                    "Content-Type: text/plain" + "\r\n" +
                    "Content-Length: 0" + "\r\n" +
                    "Lamport-Time: " + clock.getTime() + "\r\n\r\n";
            try {
                socketOut.write(RESPONSE.getBytes());
                socketOut.flush();
            } catch (IOException e) {
                System.out.println("Error writing to socket...");
            }
        }
    }
}
