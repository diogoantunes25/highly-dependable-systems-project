#!/bin/bash
# Script to run multiple clients in parallel - it assumes that the setup script 
# has been run and the replicas are already running
#
# The system has <replicas> replicas with ids from 0 to <replicas - 1>
# The script will run clients with ids from <replicas> to <clients - 1> and 
# execute <txs> transactions per client

usage() {
    echo "Usage: $0 <replicas> <clients> <txs>"
    echo "  <clients> - number of clients to run"
    echo "  <txs> - number of transactions to run per client"
    exit 1
}

# Check the number of arguments

if [ "$#" -ne 2 ]; then
    usage
fi

replicas = $1
clients = $2
txs = $3

# Create directory if it doesn't exist
cd ..
mkdir -p outputs
cd Client

# Loop from the number of replicas to the number of clients specified
for ((i = $replicas; i < $clients; i++)); do
    # Execute mvn command for each value of i, filter and save only the required lines
    mvn exec:java -DmainClass=pt.ulisboa.tecnico.hdsledger.client.loader.LoaderClient -Dexec.args="${i} $2" | grep -E 'Finished load|Mean Latency|Throughput' | tail -n 3 > "outputs/output${i}.txt" &
done

# Wait for all background processes to finish
wait

