package com.weatheraggregation.server;

import com.weatheraggregation.content.ContentServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AggregationServerTest {

    private AggregationServer server;

    @BeforeEach
    public void setup() throws IOException {
        server = new AggregationServer(4567); // default port
        server.start();
    }

    @Test
    public void testMultipleContentServersPUT() throws InterruptedException {
        // Create a pool of threads to simulate multiple content servers
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // Simulate Content Server A sending PUT requests
        executor.submit(() -> {
            ContentServer csA = new ContentServer("localhost:4567", "path_to_file_A");
            csA.sendPutRequest();
        });

        // Simulate Content Server B sending PUT requests
        executor.submit(() -> {
            ContentServer csB = new ContentServer("localhost:4567", "path_to_file_B");
            csB.sendPutRequest();
        });

        // Simulate Content Server C sending PUT requests
        executor.submit(() -> {
            ContentServer csC = new ContentServer("localhost:4567", "path_to_file_C");
            csC.sendPutRequest();
        });

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS); // Wait for all clients to finish

        // Validate the state of the Aggregation Server (check the storage or response)
        assertTrue(server.weatherDataMap.size() > 0, "Server should contain weather data after PUT requests");
    }

    @Test
    public void testMultipleGETClients() throws InterruptedException {
        // Create a pool of threads to simulate multiple GET clients
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // Simulate GET Client A requesting data
        executor.submit(() -> {
            GETClient clientA = new GETClient("localhost", 4567);
            String response = clientA.sendGetRequest("all");
            assertNotNull(response, "GET response should not be null");
        });

        // Simulate GET Client B requesting data
        executor.submit(() -> {
            GETClient clientB = new GETClient("localhost", 4567);
            String response = clientB.sendGetRequest("IDS60901");
            assertNotNull(response, "GET response should not be null");
        });

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS); // Wait for all clients to finish
    }

    @Test
    public void testLamportClockOrder() throws InterruptedException {
        // Simulate Content Servers PUTing data with different Lamport timestamps
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            ContentServer csA = new ContentServer("localhost", 4567, "path_to_file_A");
            csA.sendPutRequestWithLamportClock(1); // Simulate Lamport clock value
        });

        executor.submit(() -> {
            ContentServer csB = new ContentServer("localhost", 4567, "path_to_file_B");
            csB.sendPutRequestWithLamportClock(3); // Simulate Lamport clock value
        });

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Validate the Lamport clock order
        assertTrue(server.validateLamportOrder(), "Lamport clock order should be maintained");
    }
}