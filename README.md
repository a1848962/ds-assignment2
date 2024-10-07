# DS Assignment 2 - a1848962 Tobias Grice

## Execution
This package can be compiled and tested using Maven.
`mvn clean compile`
`mvn test`


To run manually, **use the provided run configurations in IntelliJ**, or run in a terminal using Makefile commands.
Please note, these commands require Maven to be installed.
Run AS: `make run-as PORT=""` 
    - PORT can be left empty to use default
Run CS: `make run-cs SERVER="" WD_FILE=""` 
    - will need to provide SERVER address and WD_FILE path
Run GC: `make run-gc SERVER=""` 
    - will need to provide aggregation server address


For convenience, the following Makefile commands can be used to prefill arguments with default/testing values
`make run-gc-test`
    - Run GC with SERVER="localhost:4567"
`make run-cs-test1`
    - Run CS with SERVER="localhost:4567" WD_FILE="data/data1"
`make run-cs-test2`
    - Run CS with SERVER="localhost:4567" WD_FILE="data/data2"
`make run-cs-test3`
    - Run CS with SERVER="localhost:4567" WD_FILE="data/data3"


## Usage
Usage instructions are printed to STDOUT by each server/client at runtime. The accepted arguments for each are as follows:
- Aggregation Server accepts a single argument containing a port. If none is provided, 4567 will be used.
- Content Server requires two arguments: the first is the server address, the second is a file containing weather data.
- Get Client requires a single argument containing the server address. If none is provided, the client will prompt for one.