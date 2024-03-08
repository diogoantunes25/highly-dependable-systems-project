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
The nodes and clients within the blockchain system should employ self-generated public and private keys, 
distributed in advance before the system's initiation. The `PKI` module is responsible for generating these 
keys.

The steps to generate the keys are as follows:

### Install PKI module
```bash
cd PKI/
mvn clean install
```

### Generate keys
```bash
mvn compile exec:java -Dexec.args="w <path-to-private-key>.priv <path-to-public-key>.pub"
```


# Configuration Files

### Node configuration

Can be found inside the `resources/` folder of the `Service` module.

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

The `port` is the port used by the HDSLedgerService, to receive requests from the 
client and to respond to them. The `port2` is the port used by the NodeService for the QBFT messages.

## Dependencies

To install the necessary dependencies run the following commands:

```bash
chmod +x install_deps.sh
./install_deps.sh
```

This should install the following dependencies:

- [Google's Gson](https://github.com/google/gson) - A Java library that can be used to convert Java Objects into their JSON representation.

## Puppet Master

The puppet master is a python script `puppet-master.py` which is responsible for starting the nodes of the blockchain.
The script runs with `kitty` terminal emulator by default since it's installed on the RNL labs.

To run the script you need to have `python3` installed.
The script has arguments which can be modified:

- `terminal` - the terminal emulator used by the script
- `server_config` - a string from the array `server_configs` which contains the possible configurations for the blockchain nodes

Run the script with the following command:

```bash
python3 puppet-master.py
```
Note: You may need to install **kitty** in your computer

## Maven

It's also possible to run the project manually by using Maven.

### Instalation

Compile and install all modules using:

```bash
cd HDSLedger/
mvn clean install
```

### Execution
The clients and the blockchain nodes can be mannualy executed using the following command: (one for each terminal window)

```bash
cd <module>/
mvn compile exec:java -Dexec.args="<id>"
```

Where `<module>` is either `Service` or `Client`.

Note: The `id` is the id of the blockchain node or the clients, according to the config file. For simplicity, the first `N-1` ids are for the blockchain nodes and the remaining 
for the clients.

## Running the tests

To test the system, a lot of tests were implemented. To run everyone of them, use the following command:

```bash
cd HDSLedger/
mvn test
```

If you only want to run the tests of a specific module, use the following command:

```bash
cd <module>/
mvn test
```

Where `<module>` is either `Communication`, `Consensus`, `PKI`, or `Service`.

If you want to run a specific test class, use the following command:

```bash
cd <module>/
mvn test -Dtest=<test-class>
```

Where `<test-class>` is the name of the test class you want to run.

If you want to run a specific test of a specific test class, use the following command:

```bash
cd <module>/
mvn test -Dtest=<test-class>#<test-method>
```

Where `<test-method>` is the name of the test method you want to run.

---
