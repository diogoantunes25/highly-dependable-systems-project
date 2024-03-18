package pt.ulisboa.tecnico.hdsledger.consensus;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/*
 * Starts timeout and notifies when it ends
 * Thread-safe.
 */
public class SimpleTimer implements Timer {

	private AtomicInteger id = new AtomicInteger(0);

	private Set<Integer> used = ConcurrentHashMap.newKeySet();

	private Set<Integer> running = ConcurrentHashMap.newKeySet();

	private Queue<Consumer<Integer>> callbacks = new ConcurrentLinkedQueue<>();

	public SimpleTimer() {

	}

	public void setTimerToRunning(int timerId, int timeout) {
		boolean isNew = used.add(timerId);
		running.add(timerId);

		if (isNew) {
			Thread t = new Thread(() -> {
				try {
					Thread.sleep(timeout);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				// If timer was not cancelled, notify callbacks
				if (this.running.contains(timerId)) {
					this.callbacks.forEach(c -> c.accept(timerId));
				}
			});

			t.start();
		}
	}

	public void setTimerToStopped(int id) {
		used.add(id);
		running.remove(id);
	}

	public void registeTimeoutCallback(Consumer<Integer> callback) {
		this.callbacks.add(callback);
	}
}
