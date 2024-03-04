package pt.ulisboa.tecnico.hdsledger.consensus;

import java.util.function.Consumer;

/*
 * Represents a timer that is provided to IBFT.
 */
public interface Timer {
	/**
	 * Starts timer that times out after `timeout` milliseconds.
	 * @return timer id
	*/
	public int setTimerToRunning(int timeout);

	/**
	 * Stops timer
	 * @param id timer id (as returned by setTimerToRunning)
	 */
	public void setTimerToStopped(int id);

	/**
	 * Register callback for timeouts
	 * @param callback callback takes the expired timer id
	 */
	public void registeTimeoutCallback(Consumer<Integer> callback);
}
