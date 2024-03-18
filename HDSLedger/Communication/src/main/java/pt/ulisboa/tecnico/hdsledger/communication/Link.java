package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.consensus.message.Message;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Link interface.
 */
public interface Link {

    /**
     * Register receive of Ack for provided message ids
     *
     * @param messageIds list of message for which ask are to be registered
     */
    void ackAll(List<Integer> messageIds);

    /*
     * Broadcasts a message to all nodes in the network
     *
     * @param data The message to be broadcast
     */
    void broadcast(Message data);

    /*
     * Sends a message to a specific node with guarantee of delivery
     *
     * @param nodeId The node identifier
     *
     * @param data The message to be sent
     */
    void send(int nodeId, Message data);

    /*
     * Receives a message from any node in the network (blocking)
     */
    Message receive() throws IOException, ClassNotFoundException;
}
