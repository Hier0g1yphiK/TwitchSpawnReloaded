package net.programmer.igoodie.twitchspawn;

import net.programmer.igoodie.twitchspawn.eventqueue.EventQueue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class EventQueueTests {

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("should run every task queued from many threads exactly once.")
    public void concurrentProducersTest() throws InterruptedException {
        int producers = 8;
        int tasksPerProducer = 2_000;
        EventQueue queue = new EventQueue(0);
        AtomicIntegerArray runs = new AtomicIntegerArray(producers * tasksPerProducer);
        // Last sequence number run per producer, to check each producer's queue() calls stay FIFO
        int[] lastSeen = new int[producers];
        Arrays.fill(lastSeen, -1);
        List<String> orderViolations = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(producers * tasksPerProducer);
        CountDownLatch start = new CountDownLatch(1);

        List<Thread> threads = new ArrayList<>();
        for (int p = 0; p < producers; p++) {
            int producer = p;
            Thread thread = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { return; }
                for (int i = 0; i < tasksPerProducer; i++) {
                    int id = producer * tasksPerProducer + i;
                    int sequence = i;
                    if (i % 10 == 0) {
                        // Jumps the line, so it is exempt from the FIFO check
                        queue.queueFirst("First", () -> {
                            runs.incrementAndGet(id);
                            done.countDown();
                        });
                    } else {
                        queue.queue(() -> {
                            runs.incrementAndGet(id);
                            // Only the worker thread runs tasks, so no lock is needed here
                            if (sequence <= lastSeen[producer])
                                orderViolations.add(producer + ": " + sequence + " after " + lastSeen[producer]);
                            lastSeen[producer] = sequence;
                            done.countDown();
                        });
                    }
                    if (i % 7 == 0) queue.updateThread();
                }
                queue.updateThread();
            }, "Producer " + p);
            thread.start();
            threads.add(thread);
        }

        start.countDown();
        for (Thread thread : threads) thread.join();

        Assertions.assertTrue(done.await(20, TimeUnit.SECONDS),
                "Queue stalled with " + done.getCount() + " tasks left");
        for (int id = 0; id < runs.length(); id++) {
            Assertions.assertEquals(1, runs.get(id), "Task " + id + " ran " + runs.get(id) + " times");
        }
        Assertions.assertEquals(List.of(), orderViolations);
        Assertions.assertFalse(queue.hasUnhandledEvent());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("should not stall when a task is queued right as the queue goes idle.")
    public void noLostWakeUpTest() throws InterruptedException {
        EventQueue queue = new EventQueue(0);

        // Each task finishes and the worker heads for its idle wait while the next task is being queued
        for (int i = 0; i < 20_000; i++) {
            CountDownLatch ran = new CountDownLatch(1);
            queue.queue(ran::countDown);
            queue.updateThread();
            Assertions.assertTrue(ran.await(5, TimeUnit.SECONDS), "Queue stalled on task " + i);
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("should hold tasks until updateThread is called.")
    public void startsPausedTest() throws InterruptedException {
        EventQueue queue = new EventQueue(0);
        CountDownLatch ran = new CountDownLatch(3);

        queue.queue(ran::countDown);
        queue.queue(ran::countDown);
        queue.queue(ran::countDown);

        Assertions.assertFalse(ran.await(200, TimeUnit.MILLISECONDS), "Paused queue ran tasks");
        Assertions.assertEquals(3, queue.unhandledEventCount());

        queue.updateThread();
        Assertions.assertTrue(ran.await(5, TimeUnit.SECONDS), "Queue never resumed");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("should cancel only the first upcoming sleep.")
    public void cancelUpcomingSleepTest() throws InterruptedException {
        EventQueue queue = new EventQueue(0);
        CountDownLatch done = new CountDownLatch(1);

        queue.queue(() -> {});
        queue.queueSleep(60_000);
        queue.queueSleep(10);
        queue.queue(done::countDown);
        queue.cancelUpcomingSleep();
        Assertions.assertEquals(3, queue.unhandledEventCount());

        queue.updateThread();
        Assertions.assertTrue(done.await(5, TimeUnit.SECONDS), "The 60 second sleep was not the one cancelled");
    }

}
