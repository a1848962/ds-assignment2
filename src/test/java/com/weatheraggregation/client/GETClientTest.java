package com.weatheraggregation.client;

import com.weatheraggregation.utils.LamportClock;
import com.weatheraggregation.utils.ParsingUtils;
import com.weatheraggregation.utils.ServerData;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class GETClientTest {
    @Mock
    private Socket mockSocket;
    @Mock
    private OutputStream mockOutputStream;

    private InputStream mockInputStream;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Mock socket input/output streams
        when(mockSocket.getOutputStream()).thenReturn(mockOutputStream);
    }

    @Test
    public void testSuccessfulGetRequest() throws Exception {
        // Simulate server response with headers and JSON payload
        String serverResponse = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: 67\r\n" +
                "Content-Type: application/json\r\n" +
                "Lamport-Time: 5\r\n\r\n" +
                "{\"IDS60901\": {\"id\": \"IDS60901\", \"name\": \"Adelaide\", \"state\": \"SA\"}}";


        // Mock InputStream using ByteArrayInputStream to simulate the socket's InputStream
        mockInputStream = new ByteArrayInputStream(serverResponse.getBytes());
        when(mockSocket.getInputStream()).thenReturn(mockInputStream);

        // Use the mock InputStream inside BufferedReader to simulate server response
        BufferedReader reader = new BufferedReader(new InputStreamReader(mockInputStream));

        // Read the status line separately
        String statusLine = reader.readLine();
        assertEquals("HTTP/1.1 200 OK", statusLine, "Status line should be correctly read");

        // Parse the headers from the BufferedReader
        Map<String, String> headers = ParsingUtils.parseHeaders(reader);
        assertNotNull(headers, "Headers should not be null");
        assertEquals("67", headers.get("Content-Length"));

        // Parse the JSON data
        String[] jsonErrorCode = new String[2];
        ObjectNode weatherData = ParsingUtils.parseJSON(reader, jsonErrorCode, headers);
        assertNotNull(weatherData, "Weather data should not be null");

        // Check specific fields in the weather data
        ObjectNode stationData = (ObjectNode) weatherData.get("IDS60901");
        assertNotNull(stationData);
        assertEquals("Adelaide", stationData.get("name").asText());
    }
}