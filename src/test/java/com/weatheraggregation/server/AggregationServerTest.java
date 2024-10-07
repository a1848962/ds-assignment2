package com.weatheraggregation.server;

import com.weatheraggregation.content.ContentServer;
import com.weatheraggregation.client.GETClient;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AggregationServerTest {

    private AggregationServer server;

    @BeforeEach
    public void setup() throws IOException {
        server = new AggregationServer(4567); // default port
        ExecutorService executor = Executors.newSingleThreadExecutor();

        // start AS server in executor
        executor.submit(() -> {
            try {
                server.start();
            } catch (Exception ex) {
                System.out.println("Failed to start AS server for testing: " + ex.getMessage());
            }
        });

        // allow time for server to start up before running tests
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            System.out.println("Error waiting for AS to start up: " + ex.getMessage());
        }
    }

    @Test
    @Order(1)
    public void testMultipleContentServersPUT() throws InterruptedException {
        // use a pool of threads to simulate multiple content servers
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // simulate three content servers sending put requests
        executor.submit(() -> {
            ContentServer csA = new ContentServer("localhost:4567", "data/data1");
            boolean success = csA.sendPutRequest();
            assertTrue(success, "PUT client should have received 200/201 OK");
        });
        executor.submit(() -> {
            ContentServer csB = new ContentServer("localhost:4567", "data/data2");
            boolean success = csB.sendPutRequest();
            assertTrue(success, "PUT client should have received 200/201 OK");
        });
        executor.submit(() -> {
            ContentServer csC = new ContentServer("localhost:4567", "data/data3");
            boolean success = csC.sendPutRequest();
            assertTrue(success, "PUT client should have received 200/201 OK");
        });

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS); // wait for all CSs to finish
    }

    @Test
    @Order(2)
    public void testWeatherDataMapCorrectlyPopulated() throws InterruptedException {
        // make assertions on AS state
        assertFalse(server.weatherDataMap.isEmpty(), "Server should contain weather data after PUT requests");
        assertEquals(server.weatherDataMap.size(), 3, "Server should contain exactly 3 sets of weather data");
        assertNotNull(server.weatherDataMap.get("IDS60901"), "Server should contain weather data for IDS60901");
        assertNotNull(server.weatherDataMap.get("IDS60902"), "Server should contain weather data for IDS60902");
        assertNotNull(server.weatherDataMap.get("IDS60903"), "Server should contain weather data for IDS60903");
    }

    @Test
    @Order(3)
    public void testMultipleGETClients() throws InterruptedException {
        // use a pool of threads to simulate multiple GET clients
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // simulate three GET clients sending get requests
        executor.submit(() -> {
            GETClient clientA = new GETClient("localhost:4567");
            boolean success = clientA.sendGetRequest("");
            assertTrue(success, "GET client should have received 200 OK");
        });
        executor.submit(() -> {
            GETClient clientB = new GETClient("localhost:4567");
            boolean success = clientB.sendGetRequest("");
            assertTrue(success, "GET client should have received 200 OK");
        });
        executor.submit(() -> {
            GETClient clientC = new GETClient("localhost:4567");
            boolean success = clientC.sendGetRequest("");
            assertTrue(success, "GET client should have received 200 OK");
        });

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS); // Wait for all clients to finish
    }

    @Test
    @Order(4)
    public void testExpiredDataExpunging() throws InterruptedException {
        System.out.println("Sleeping for 31 seconds to test expired data expunging...");
        Thread.sleep(31*1000);

        server.removeExpiredStations();
        assertTrue(server.weatherDataMap.isEmpty(), "Expired content should have been removed from the server");
    }
}