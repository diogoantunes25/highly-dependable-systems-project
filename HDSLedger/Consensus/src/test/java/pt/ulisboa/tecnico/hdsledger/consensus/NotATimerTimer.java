package pt.ulisboa.tecnico.hdsledger.consensus;

import java.util.concurrent.atomic.AtomicInteger; 
import java.util.function.Consumer;

/**
 * Timer that never times out
 */
public class NotATimerTimer implements Timer {

	private AtomicInteger id = new AtomicInteger(0);

	public NotATimerTimer() {}

	public int setTimerToRunning(int timeout) {
		return id.getAndIncrement();
	}

	public void setTimerToStopped(int id) {
		// nop	
	}

	public void registeTimeoutCallback(Consumer<Integer> callback) {
		// nop
	}
}
