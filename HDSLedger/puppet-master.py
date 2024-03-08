#!/usr/bin/env python

import os
import json
import sys
import signal


# Terminal Emulator used to spawn the processes
terminal = "kitty"

# Blockchain node configuration file name
server_configs = [
    "regular_config.json",
]


server_config = server_configs[0]

def quit_handler(*args):
    os.system(f"pkill -i {terminal}")
    sys.exit()


# Compile classes - skip tests as we do not need it now and it takes some time
if os.system("mvn clean install -DskipTests") != 0:
    print("Failed to compile")
    exit(1)

# Spawn blockchain nodes
with open(f"Service/src/main/resources/{server_config}") as f:
    data = json.load(f)
    processes = list()
    for key in data:
        if int(key['id']) < key['N']:  # verification done since the config file has the nodes and clients' configs and we only want
            # to spawn the nodes, which are the ones with id < N, being N the number of blockchain nodes
            pid = os.fork()
            if pid == 0:
                os.system(
                    f"{terminal} sh -c \"cd Service; mvn exec:java -Dexec.args='{key['id']}' ; sleep 500\"")
                sys.exit()

signal.signal(signal.SIGINT, quit_handler)

while True:
    print("Type quit to quit")
    command = input(">> ")
    if command.strip() == "quit":
        quit_handler()