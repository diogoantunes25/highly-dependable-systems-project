#! /bin/bash

die() {
	cat << EOF
	Usage: <n_nodes> <n_replicas> <config_file>

	Script to setup public key infrastructure.
EOF
	
	exit 1
}

if [[ $# -ne 3 ]]; then
	die
fi

n=$1
n2=$2
config=$3

cd PKI || exit 1
mvn clean install -DskipTests

for i in $(seq 0 $((n+n2))); do
	mvn exec:java -Dexec.args="w /tmp/priv_$i.key /tmp/pub_$i.key"
done

echo "[" > $config

i=0
cat >> $config <<EOF
    {
        "id": "$i",
        "hostname": "localhost",
		"port": $((3000+i)),
        "port2": $((4000+i)),
        "N": $n,
        "publicKeyPath": "/tmp/pub_$i.key",
        "privateKeyPath": "/tmp/priv_$i.key"
EOF

for i in $(seq 1 $n); do
	cat >> $config <<EOF
	},
    {
        "id": "$i",
        "hostname": "localhost",
		"port": $((3000+i)),
        "port2": $((4000+i)),
        "N": $n,
        "publicKeyPath": "/tmp/pub_$i.key",
        "privateKeyPath": "/tmp/priv_$i.key"
EOF
done

for i in $(seq $n $((n+n2))); do
	cat >> $config <<EOF
	},
    {
        "id": "$i",
        "hostname": "localhost",
		"port": $((3000+i)),
        "port2": -1,
        "N": $n,
        "publicKeyPath": "/tmp/pub_$i.key",
        "privateKeyPath": "/tmp/priv_$i.key"
EOF
done

echo "} ]" >> $config
