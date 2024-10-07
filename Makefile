# define classes
AGGREGATION_SERVER = com.weatheraggregation.server.AggregationServer
CONTENT_SERVER = com.weatheraggregation.content.ContentServer
GET_CLIENT = com.weatheraggregation.client.GETClient

# RUN WITH USER-PROVIDED ARGUMENTS (STANDARD)
run-as:
	mvn exec:java -Dexec.mainClass=$(AGGREGATION_SERVER) -Dexec.args="$(PORT)"

run-cs:
	mvn exec:java -Dexec.mainClass=$(CONTENT_SERVER) -Dexec.args="$(SERVER) $(WD_FILE)"

run-gc:
	mvn exec:java -Dexec.mainClass=$(GET_CLIENT) -Dexec.args="$(SERVER)"


# RUN WITH PREFILLED ARGUMENTS (DEFAULT / TESTING VALUES)
run-cs-test1:
	make run-cs SERVER="localhost:4567" WD_FILE="data/data1"

run-cs-test2:
	make run-cs SERVER="localhost:4567" WD_FILE="data/data2"

run-cs-test3:
	make run-cs SERVER="localhost:4567" WD_FILE="data/data3"

run-gc-test:
	make run-gc SERVER="localhost:4567"