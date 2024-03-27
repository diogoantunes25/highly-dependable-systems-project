#!/bin/bash
#
# script to calculate the mean of mean latency and total throughput 
# from the output files generated by doing a load test on the blockchain network

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <start_index> <end_index>"
    exit 1
fi

output_folder="outputs"
start_index=$1
end_index=$2
mean_latency_sum=0
total_throughput=0
num_files=0

# Loop through files in the output folder
for ((i=start_index; i<=end_index; i++)); do
    filename="$output_folder/output${i}.txt"
    if [ -f "$filename" ]; then
        mean_latency=$(awk '/Mean Latency:/ {print $3}' "$filename")
        throughput=$(awk '/Throughput:/ {print $2}' "$filename")
        mean_latency_sum=$(echo "$mean_latency_sum + $mean_latency" | bc)
        total_throughput=$(echo "$total_throughput + $throughput" | bc)
        num_files=$((num_files + 1))
    fi
done

if [ "$num_files" -gt 0 ]; then
    mean_mean_latency=$(echo "scale=4; $mean_latency_sum / $num_files" | bc)
    echo "Mean of Mean Latency: $mean_mean_latency ms"
    echo "Total Throughput: $total_throughput transactions per second"
else
    echo "No files found in the specified range."
fi
