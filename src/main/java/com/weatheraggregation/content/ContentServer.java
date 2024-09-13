package com.weatheraggregation.content;

/*
* Read two parameters from the command line:
* 1. server name and port number (as for GET)
* 2. location of a file in the file system local to the Content Server (expected to be in your project folder).
*    The file will contain weather data to be assembled into JSON format and then uploaded to the server.
*
* EXAMPLE PUT MESSAGE:

PUT /weather.json HTTP/1.1
User-Agent: ATOMClient/1/0
Content-Type: (You should work this one out)
Content-Length: (And this one too)
{
    "id" : "IDS60901",
    ... (DATA HERE) ...
    "wind_spd_kt": 8
}

* Use an existing JSON parser
* Implement this assignment using Sockets rather than HttpServer
*
* */
public class ContentServer {
}
