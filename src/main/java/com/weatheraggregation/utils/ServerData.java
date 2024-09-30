package com.weatheraggregation.utils;

import java.net.URI;
import java.net.URISyntaxException;

/* CLASS TO STORE CONNECTION DATA FOR A SERVER */
public class ServerData {
    public String protocol;
    public String name;
    public String domain;
    public int port;

    // function to parse server information into ServerData object
    public ServerData(String arg) {
        try {
            if (arg.startsWith("http")) {
                // if http address, handle using URI class
                URI uri = new URI(arg);
                this.protocol = uri.getScheme(); // returns protocol
                String host = uri.getHost(); // returns server name and domain
                this.port = uri.getPort();  // returns port (-1 if not provided)

                // check if domain is specified
                if (host != null && host.contains(".")) {
                    // split domain from host
                    String[] hostSplit = host.split("\\.", 2);
                    this.name = hostSplit[0];
                    this.domain = hostSplit.length > 1 ? hostSplit[1] : null;
                } else {
                    this.name = host; // host variable is server name, no domain specified
                }
            } else {
                // no protocol, servername:portnumber format
                this.protocol = "http"; // assume http default
                String[] split = arg.split(":");
                if (split.length == 2) {
                    this.name = split[0];
                    this.port = Integer.parseInt(split[1]);
                } else {
                    throw new IllegalArgumentException("Invalid format. Expected servername:portnumber at minimum.");
                }
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI format: " + e.getMessage());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port number must be a positive integer.");
        }

        if (this.port < 0) {
            throw new IllegalArgumentException("Port number must be specified and must be a positive integer.");
        }
    }
}

