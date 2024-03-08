package pt.ulisboa.tecnico.hdsledger.consensus;

import java.util.concurrent.atomic.AtomicInteger; 
import java.util.function.Consumer;

/**
 * Timer that never times out
 */
public class NotATimerTimer implements Timer {

	private AtomicInteger id = new AtomicInteger(0);

	public NotATimerTimer() {}

	public void setTimerToRunning(int timerId, int timeout) {
		// nop
	}

	public void setTimerToStopped(int id) {
		// nop	
	}

	public void registeTimeoutCallback(Consumer<Integer> callback) {
		// nop
	}
}
