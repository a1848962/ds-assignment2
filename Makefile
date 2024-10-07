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
run-cs-auto1:
	mvn exec:java -Dexec.mainClass=$(CONTENT_SERVER) -Dexec.args="localhost:4567 data/data1"

run-cs-auto2:
	mvn exec:java -Dexec.mainClass=$(CONTENT_SERVER) -Dexec.args="localhost:4567 data/data2"

run-cs-auto3:
	mvn exec:java -Dexec.mainClass=$(CONTENT_SERVER) -Dexec.args="localhost:4567 data/data3"

run-gc-auto:
	mvn exec:java -Dexec.mainClass=$(GET_CLIENT) -Dexec.args="localhost:4567"