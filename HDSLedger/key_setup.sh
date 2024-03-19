#!/bin/bash

# Check if correct number of arguments are provided
if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <n_replicas> <n_clients> <config_path>"
    exit 1
fi

n_replicas=$1
n_clients=$2
config_path=$3

# Function to generate JSON objects for replicas
generate_replica_config() {
    local i=$1
    local json="{"
    json+="\"id\": \"$i\","
    json+="\"hostname\": \"localhost\","
    json+="\"port\": $((3000+i)),"
    json+="\"port2\": $((4000+i)),"
    json+="\"N\": $n_replicas,"
    json+="\"publicKeyPath\": \"/tmp/node$i.pub\","
    json+="\"privateKeyPath\": \"/tmp/node$i.priv\""
    json+="}"
    echo "$json"
}

# Function to generate JSON objects for clients
generate_client_config() {
    local j=$1
    local json="{"
    json+="\"id\": \"$j\","
    json+="\"hostname\": \"localhost\","
    json+="\"port\": $((3000+j)),"
    json+="\"port2\": -1,"
    json+="\"N\": $n_replicas,"
    json+="\"publicKeyPath\": \"/tmp/client$j.pub\","
    json+="\"privateKeyPath\": \"/tmp/client$j.priv\""
    json+="}"
    echo "$json"
}

# Change directory to PKI
cd PKI || exit

# Generate configs for replicas
config="["
for ((i=0; i<n_replicas; i++)); do
    mvn exec:java -Dexec.args="w /tmp/node$i.priv /tmp/node$i.pub"
    config+=$(generate_replica_config "$i")
    config+=","
done

# Generate configs for clients
for ((j=n_replicas; j<n_replicas+n_clients; j++)); do
    mvn exec:java -Dexec.args="w /tmp/client$j.priv /tmp/client$j.pub"
    config+=$(generate_client_config "$j")
    if [ "$j" -ne "$((n_replicas + n_clients - 1))" ]; then
        config+=","
    fi
done
config+="]"

cd ../
# Write the config to the specified path
echo "$config" > "$config_path"

echo "Configuration generated and saved to $config_path"
