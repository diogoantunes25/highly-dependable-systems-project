# HDSLedger

## Introduction

HDSLedger is a simplified permissioned (closed membership) blockchain system with high dependability
guarantees. It uses the Istanbul BFT consensus algorithm to ensure that all nodes run commands
in the same order, achieving State Machine Replication (SMR) and guarantees that all nodes
have the same state.

## Requirements

- [Java 17](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html) - Programming language;

- [Maven 3.8](https://maven.apache.org/) - Build and dependency management tool;

- [Python 3](https://www.python.org/downloads/) - Programming language;

---

# Public Key Infrastructure
Both nodes and client assume that a public-key infrastructure was setup in advance.
The `PKI` module can be used to setup this infrastructure.

To generate a single key pair:

```bash
$ cd HDSLedger/PKI
$ mvn clean install
$ mvn exec:java -Dexec.args="w <path-to-private-key>.priv <path-to-public-key>.pub"
```

The setup of the PKI for `<n>` nodes and `<c>` clients is explained further below.

# Configuration Files

## Node configuration

By default both nodes and replicas look for the config file inside the `resources/` folder
of the `Service` module. A config is an array of objects, each one describing
a participant of the system. For each one, it should exist an object with the
following syntax, with `port` being the port used by the ledger service, for replicas
to interact with the clients and `port2` being used by the replicas for the consensus.

```json
{
    "id": <NODE_ID>,
    "hostname": "localhost",
    "port": <NODE_PORT>,
    "port2": <NODE_PORT2>,
    "N": <NUMBER_OF_NODES>,
    "publicKeyPath": "<PATH_TO_PUBLIC_KEY>",
    "privateKeyPath": "<PATH_TO_PRIVATE_KEY>",
}
```

> Note: For simplicity, the first `N-1` ids are reserved for the replicas and the remaining 
> for the clients.

## Genesis file

The genesis file defines the initial balances in the system when it boots. The syntax is as follows:

```json

[
    { "id": 0, "balance": 10},
    { "id": 1, "balance": 15},
    ...
    { "id": 10, "balance": 1}
]
```

## HDS Configuration
In order to ease the setup of the system, a script was created to generate the PKI for `<n>` nodes and `<c>` clients, the configuration file on `Service/src/main/regular_config.json>` and the genesis file on `</tmp/gensis.json>` with <initial-balance> for every participant.

To run the script, execute the following command:

```bash
$ cd HDSLedger/
$ chmod +x setup.sh
$ ./setup.sh <n> <c> <path/to/config-file.json> <initial-balance>
```

## Dependencies

To install the necessary dependencies run the following commands:

```bash
$ chmod +x install_deps.sh
$ ./install_deps.sh
```

This should install the following dependencies:

- [Google's Gson](https://github.com/google/gson) - A Java library that can be used to convert Java Objects into their JSON representation.

## Puppet Master

The puppet master is a python script `puppet-master.py` which is responsible for starting the replicas.
The script runs with `kitty` terminal emulator by default since it's installed on the RNL labs.

To run the script you need to have `python3` installed.
The script has arguments which can be modified:

- `terminal` - the terminal emulator used by the script
- `server_config` - a string from the array `server_configs` which contains the possible configurations for the replicas

Run the script with the following command:

```bash
$ python3 puppet-master.py
```
Note: You may need to install **kitty** in your computer

## Maven

It's also possible to run the project manually by using Maven.

### Instalation

Compile and install all modules using:

```bash
$ cd HDSLedger/
$ mvn clean install -DskipTests
```

### Execution
The clients and the replicas can be manually started by running

```bash
$ cd <module>/
$ mvn exec:java -Dexec.args="<id>"
```

Where `<module>` is either `Service` or `Client`.

## Running the tests

To run unit tests, Maven can be used as follows:

```bash
$ cd HDSLedger/
$ mvn test
```

---
