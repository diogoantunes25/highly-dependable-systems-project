#!/bin/bash

# Initialize variables
total_throughput=0
total_latencies=0
count=0

# Read each line from input
while read -r line; do
    if [[ $line == *"Throughput"* ]]; then
        # Extract throughput value and add to total_throughput
        throughput=$(echo "$line" | awk '{print $2}')
        total_throughput=$(awk "BEGIN {print $total_throughput + $throughput}")
    elif [[ $line == *"Mean Latency"* ]]; then
        # Extract latency value and add to total_latencies
        latency=$(echo "$line" | awk '{print $3}')
        total_latencies=$(awk "BEGIN {print $total_latencies + $latency}")
        count=$((count + 1))
    fi
done

# Calculate mean latency
mean_latency=$(awk "BEGIN {print $total_latencies / $count}")

# Print results
echo "Sum of Throughput: $total_throughput transactions per second"
echo "Mean of Latencies: $mean_latency ms"
echo "$total_throughput,$mean_latency"
