package com.weatheraggregation.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

public class ParsingUtilsTest {

    /* PARSE HEADERS TESTING */

    @Test
    public void testParseValidHeaders() throws IOException {
        String headersString = "Content-Length: 10\r\nContent-Type: application/json\r\n\r\n";
        BufferedReader reader = new BufferedReader(new StringReader(headersString));

        Map<String, String> headers = ParsingUtils.parseHeaders(reader);
        assertEquals(2, headers.size(), "Wrong number of headers");
        assertEquals("10", headers.get("Content-Length"), "Wrong header for Content-Length");
        assertEquals("application/json", headers.get("Content-Type"), "Wrong header value for Content-Type");
    }

    @Test
    public void testParseHeadersWithInvalidLine() {
        String headersString = "Content-Length: 10\r\nInvalidHeader\r\n\r\n";
        BufferedReader reader = new BufferedReader(new StringReader(headersString));

        assertThrows(IOException.class, () -> ParsingUtils.parseHeaders(reader), "parseHeaders should have thrown IO exception for invalid header");
    }

    @Test
    public void testParseEmptyHeaders() throws Exception {
        String headersStr = "\r\n";
        BufferedReader reader = new BufferedReader(new StringReader(headersStr));

        Map<String, String> headers = ParsingUtils.parseHeaders(reader);
        assertTrue(headers.isEmpty(), "Headers should be empty");
    }


    /* PARSE JSON TESTING */

    @Test
    public void testParseValidJsonPayload() throws Exception {
        // sample payload (valid json)
        String headersStr = "Content-Length: 132\r\nContent-Type: application/json\r\n\r\n";
        String jsonPayload = "{\"IDS60901\":{\"id\":\"IDS60901\",\"name\":\"Adelaide (West Terrace / ngayirdapira)\",\"state\":\"SA\",\"apparent_t\":9.5,\"cloud\":\"Partly cloudy\"}}";

        // following code written with the assistance of a LLM
        BufferedReader reader = new BufferedReader(new StringReader(jsonPayload));
        String[] errorCode = new String[2];
        Map<String, String> headers = ParsingUtils.parseHeaders(new BufferedReader(new StringReader(headersStr)));

        ObjectNode jsonNode = ParsingUtils.parseJSON(reader, errorCode, headers);
        assertNotNull(jsonNode, "JSON should be parsed successfully");

        ObjectNode stationData = (ObjectNode) jsonNode.get("IDS60901");
        assertNotNull(stationData, "Station data for IDS60901 should exist");
        // end LLM assistance

        assertEquals("IDS60901", stationData.get("id").asText());
        assertEquals("Adelaide (West Terrace / ngayirdapira)", stationData.get("name").asText());
        assertEquals("SA", stationData.get("state").asText());
        assertEquals("9.5", stationData.get("apparent_t").asText());
        assertEquals("Partly cloudy", stationData.get("cloud").asText());
    }

    @Test
    public void testParseJsonWithInvalidContentLength() throws Exception {
        String headersStr = "Content-Length: 200\r\nContent-Type: application/json\r\n\r\n";
        String jsonPayload = "{\"IDS60901\":{\"id\":\"IDS60901\",\"name\":\"Adelaide (West Terrace / ngayirdapira)\",\"state\":\"SA\",\"apparent_t\":9.5,\"cloud\":\"Partly cloudy\"}}";

        BufferedReader reader = new BufferedReader(new StringReader(jsonPayload));
        String[] errorCode = new String[2];
        Map<String, String> headers = ParsingUtils.parseHeaders(new BufferedReader(new StringReader(headersStr)));

        ObjectNode jsonNode = ParsingUtils.parseJSON(reader, errorCode, headers);

        assertNull(jsonNode, "JSON should not be parsed due to invalid content length");
        assertEquals("400 Bad Request", errorCode[0], "Status code should be 400 Bad Request for incomplete payload");
        assertEquals("Incomplete payload received", errorCode[1], "Client error should reflect incomplete payload");
    }

    @Test
    public void testParseJsonWithNonJsonContentType() throws Exception {
        String headersStr = "Content-Length: 132\r\nContent-Type: text/plain\r\n\r\n";
        String jsonPayload = "{\"IDS60901\":{\"id\":\"IDS60901\",\"name\":\"Adelaide (West Terrace / ngayirdapira)\",\"state\":\"SA\",\"apparent_t\":9.5,\"cloud\":\"Partly cloudy\"}}";

        BufferedReader reader = new BufferedReader(new StringReader(jsonPayload));
        String[] errorCode = new String[2];
        Map<String, String> headers = ParsingUtils.parseHeaders(new BufferedReader(new StringReader(headersStr)));

        ObjectNode jsonNode = ParsingUtils.parseJSON(reader, errorCode, headers);

        assertNull(jsonNode, "JSON should not be parsed due to incorrect content type");
        assertEquals("400 Bad Request", errorCode[0], "Status code should be 400 Bad Request for incorrect content type");
        assertEquals("Content-Type is not application/json", errorCode[1], "Client error should reflect incorrect content type");
    }

    @Test
    public void testParseEmptyPayload() throws Exception {
        String headersStr = "Content-Length: 0\r\nContent-Type: text/plain\r\n\r\n";
        String jsonPayload = "";

        BufferedReader reader = new BufferedReader(new StringReader(jsonPayload));
        String[] errorCode = new String[2];
        Map<String, String> headers = ParsingUtils.parseHeaders(new BufferedReader(new StringReader(headersStr)));

        ObjectNode jsonNode = ParsingUtils.parseJSON(reader, errorCode, headers);

        assertNull(jsonNode, "No JSON should be parsed due to empty payload");
        assertEquals("204 No Content", errorCode[0], "Status code should be 204 No Content for empty payload");
        assertEquals("Empty payload", errorCode[1], "Client error should reflect empty payload");
    }


    @Test
    public void testParseInvalidJsonPayload() throws Exception {
        // set content length to 100 (actual content size is 132)
        // json will be cut at 100 chars (invalid json)
        String headersStr = "Content-Length: 100\r\nContent-Type: application/json\r\n\r\n";
        String jsonPayload = "{\"IDS60901\":{\"id\":\"IDS60901\",\"name\":\"Adelaide (West Terrace / ngayirdapira)\",\"state\":\"SA\",\"apparent_t\":9.5,\"cloud\":\"Partly cloudy\"}}";

        BufferedReader reader = new BufferedReader(new StringReader(jsonPayload));
        String[] errorCode = new String[2];
        Map<String, String> headers = ParsingUtils.parseHeaders(new BufferedReader(new StringReader(headersStr)));

        ObjectNode jsonNode = ParsingUtils.parseJSON(reader, errorCode, headers);

        assertNull(jsonNode, "No JSON should be parsed due to invalid JSON");
        assertEquals("500 Internal Server Error", errorCode[0], "Status code should be 500 Internal Server Error for invalid JSON");
        assertTrue(errorCode[1].contains("Invalid JSON"), "Client error should reflect invalid JSON");
    }
}