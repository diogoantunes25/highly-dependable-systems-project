package pt.ulisboa.tecnico.hdsledger.consensus;

import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.consensus.message.*;

import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

/**
 * Instance of Instanbul consensus protocol
 *
 * @param <V> Type of value that is being decided on
 *
 * TODO (dsa): add assumptions on atomicity
 */

public class Instanbul<V> {

	// Process configuration (includes its id)
	private final ProcessConfig config;

	// The identifier of the consensus instance
	private final int lambda;

	// The current round
	private Optional<Integer> ri;

	// The round at which the process has prepared
	private Optional<Integer> pri;

	// The value for which the process has prepared
	private Optional<V> pvi;

	// The value passed as input to this instance
	private Optional<V> inputValuei;

	public Instanbul(ProcessConfig config, int lambda) {
		this.lambda = lambda;
		this.config = config;

		this.ri = Optional.empty();
		this.pri = Optional.empty();
		this.pvi = Optional.empty();
		this.inputValuei = Optional.empty();
	}


	/*
     * Start an instance of consensus for a value
     * Only the current leader will start a consensus instance,
     * the remaining nodes only update values.
     *
     * @param inputValue Value to value agreed upon
     */
	public List<Message> start(V inputValue) {

		// TODO: copy from NodeService
        throw new UnsupportedOperationException("TODO");
	}

	/*
     * Handle pre prepare messages and if the message
     * came from leader and is justified them broadcast prepare
     *
     * @param message Message to be handled
     */
	public List<Message> prePrepare(PrePrepareMessage message) {

		// TODO: copy from NodeService
        throw new UnsupportedOperationException("TODO");
	}

    /*
     * Handle prepare messages and if there is a valid quorum broadcast commit
     *
     * @param message Message to be handled
     */
	public List<Message> prepare(PrepareMessage message) {

		// TODO: copy from NodeService
        throw new UnsupportedOperationException("TODO");
	}

	public List<Message> commit(CommitMessage message) {

		// TODO: copy from NodeService
        throw new UnsupportedOperationException("TODO");
	}

	// TODO: add stuff for round change
}
