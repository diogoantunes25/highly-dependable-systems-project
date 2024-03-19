package pt.ulisboa.tecnico.hdsledger.consensus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.io.TempDir;

import pt.ulisboa.tecnico.hdsledger.communication.consensus.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.pki.RSAKeyGenerator;

import java.security.*;
import java.nio.file.Path;
import java.io.File;
import java.io.IOException;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.List;
import java.util.Optional;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.util.Pair;

public class TimerTest {

    /**
     * Simple test that starts a timer and checks if it triggers the callback
     */
    @Test
    public void simpleTest() throws InterruptedException {

        Timer timer = new SimpleTimer();
        int timeout = 1000;
        int round = 0; 
        timer.setTimerToRunning(round, timeout);
        AtomicBoolean triggered = new AtomicBoolean(false);

        Consumer<Integer> callback = timerId -> {
            System.out.printf("Timer %d expired\n", timerId);
            triggered.set(true);
        };

        timer.registeTimeoutCallback(callback);

        Thread.sleep(100);

        assertTrue(!triggered.get());

        Thread.sleep(timeout);

        assertTrue(triggered.get());
    }

    /**
     * Test that starts a timer, stops it and checks if it triggers the callback
     */
        @Test
        public void stopTimerTest() throws InterruptedException{

            Timer timer = new SimpleTimer();
            int timeout = 1000;
            int round = 0; 
            AtomicBoolean triggered = new AtomicBoolean(false);

            Consumer<Integer> callback = timerId -> {
                //System.out.printf("Timer %d expired\n", timerId);
                triggered.set(true);
            };

            timer.registeTimeoutCallback(callback);

            timer.setTimerToRunning(round, timeout);

            timer.setTimerToStopped(round);

            Thread.sleep(timeout + 100);

            assertFalse(triggered.get());

        }

    /**
     * Test that starts a timer, stops it, starts it again and checks if it triggers the callback
     */
    @Test
    public void stopAndStartTest() throws InterruptedException{

        Timer timer = new SimpleTimer();
        int timeout = 1000;
        int round = 0; 
        AtomicBoolean triggered = new AtomicBoolean(false);

        Consumer<Integer> callback = timerId -> {
            System.out.printf("Timer %d expired\n", timerId);
            triggered.set(true);
        };

        timer.registeTimeoutCallback(callback);

        timer.setTimerToStopped(round);

        timer.setTimerToRunning(round, timeout);

        Thread.sleep(timeout + 100);

        assertFalse(triggered.get());
    }

    /**
     * Test that starts a timer twice, ensure only the first started
     */
    @Test
    public void startTwiceTest() throws InterruptedException{

        Timer timer = new SimpleTimer();
        int timeout = 1000;
        int round = 0; 
        AtomicInteger triggered = new AtomicInteger(0);

        Consumer<Integer> callback = timerId -> {
            System.out.printf("Timer %d expired\n", timerId);
            triggered.getAndIncrement();
        };

        timer.registeTimeoutCallback(callback);

        timer.setTimerToRunning(round, timeout);

        Thread.sleep(timeout + 200);

        timer.setTimerToRunning(round, timeout);

        Thread.sleep(timeout*2);

        assertEquals(triggered.get(), 1);
    }
}
