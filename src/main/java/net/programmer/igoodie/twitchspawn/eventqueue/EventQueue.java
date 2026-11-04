package net.programmer.igoodie.twitchspawn.eventqueue;

import net.minecraft.server.level.ServerPlayer;
import net.programmer.igoodie.twitchspawn.TwitchSpawn;
import net.programmer.igoodie.twitchspawn.network.NetworkManager;
import net.programmer.igoodie.twitchspawn.network.packet.GlobalChatCooldownPacket;
import net.programmer.igoodie.twitchspawn.tslanguage.event.EventArguments;
import net.programmer.igoodie.twitchspawn.tslanguage.event.TSLEvent;
import net.programmer.igoodie.twitchspawn.util.CooldownBucket;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class EventQueue {

    // Guards tasks, state, waitingForServer and the counters. Producers (socket tracers, the server thread)
    // and the worker thread all go through it, and the worker waits on it.
    private final Object lock = new Object();
    private final Thread innerThread;
    private volatile EventQueueState state;
    private final Deque<EventQueueTask> tasks;
    private boolean waitingForServer;
    private long cooldown; // milliseconds
    private int succeededEvents;
    private int discardedEvents;

    public EventQueue(long cooldownDuration) {
        this.innerThread = new Thread(() -> {
            while (true) stepThread();
        }, "TwitchSpawn Event Queue");
        this.innerThread.setDaemon(true); // Never keeps the game running after it closes
        this.state = EventQueueState.PAUSED;
        this.tasks = new ArrayDeque<>();
        this.cooldown = cooldownDuration;

        this.innerThread.start();
    }

    private void stepThread() {
        try {
            EventQueueTask task = takeTask();

            task.run();

            if (task.getType() == EventQueueTask.Type.SLEEP) {
                synchronized (lock) {
                    if (state == EventQueueState.COOLDOWN) state = EventQueueState.WORKING;
                }
            }

        } catch (Throwable e) {
            synchronized (lock) {
                discardedEvents++;
            }
            e.printStackTrace(); // TODO:
        }
    }

    /**
     * Blocks until the queue is unpaused, the server finished the previous TSL event and a task is available.
     * The conditions are re-checked under the lock after every wake-up, so no notify can slip in between.
     */
    private EventQueueTask takeTask() throws InterruptedException {
        synchronized (lock) {
            while (true) {
                if (tasks.isEmpty()) state = EventQueueState.PAUSED;
                if (state != EventQueueState.PAUSED && !waitingForServer) break;
                lock.wait();
            }

            EventQueueTask task = tasks.removeFirst();
            if (task.getType() == EventQueueTask.Type.SLEEP)
                state = EventQueueState.COOLDOWN;
            return task;
        }
    }

    public void updateThread() {
        synchronized (lock) {
            if (state == EventQueueState.PAUSED) {
                state = EventQueueState.WORKING;
                lock.notifyAll();
            }
        }
    }

    public void cancelUpcomingSleep() {
        synchronized (lock) {
            Iterator<EventQueueTask> iterator = tasks.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getType() == EventQueueTask.Type.SLEEP) {
                    iterator.remove();
                    return;
                }
            }
        }
    }

    public void queueSleepFirst() {
        queueSleepFirst(cooldown);
    }

    public void queueSleepFirst(long millis) {
        addFirst(new EventQueueTask("Sleep", millis));
    }

    public void queueSleep() {
        queueSleep(cooldown);
    }

    public void queueSleep(long millis) {
        addLast(new EventQueueTask("Sleep", millis));
    }

    public void queueFirst(String name, Runnable task) {
        addFirst(new EventQueueTask(name, task));
    }

    public void queue(Runnable task) {
        queue("Runnable task", task);
    }

    public void queue(String name, Runnable task) {
        addLast(new EventQueueTask(name, task));
    }

    public void queue(TSLEvent eventNode, EventArguments args, CooldownBucket cooldownBucket) {
        if (eventNode.willPerform(args)) {
            if (cooldownBucket != null) {
                cooldownBucket.consume(args.actorNickname);

                ServerPlayer playerEntity = TwitchSpawn.SERVER
                        .getPlayerList()
                        .getPlayerByName(args.streamerNickname);

                if (playerEntity != null) {
                    NetworkManager.sendTo(playerEntity, new GlobalChatCooldownPacket(cooldownBucket.getGlobalCooldownTimestamp()));
                }
            }
        }

        addLast(new EventQueueTask("TSL Event task", () -> {
            synchronized (lock) {
                waitingForServer = true;
            }
            try {
                TwitchSpawn.SERVER.execute(() -> {
                    boolean performed = false;
                    try {
                        performed = eventNode.process(args);

                    } catch (Throwable e) {
                        // TODO: "Event failed HUD" maybe?

                    } finally {
                        serverTaskFinished(performed);
                    }
                });
            } catch (Throwable e) {
                // The server refused the task, so nothing will ever clear waitingForServer
                synchronized (lock) {
                    waitingForServer = false;
                    lock.notifyAll();
                }
                throw e;
            }
        }));
    }

    private void serverTaskFinished(boolean performed) {
        synchronized (lock) {
            if (performed) succeededEvents++;
            else discardedEvents++;
            waitingForServer = false;
            lock.notifyAll();
        }
    }

    private void addFirst(EventQueueTask task) {
        synchronized (lock) {
            tasks.addFirst(task);
        }
    }

    private void addLast(EventQueueTask task) {
        synchronized (lock) {
            tasks.addLast(task);
        }
    }

    public int succeededEventCount() {
        synchronized (lock) {
            return succeededEvents;
        }
    }

    public int discardedEventCount() {
        synchronized (lock) {
            return discardedEvents;
        }
    }

    public int unhandledEventCount() {
        synchronized (lock) {
            return tasks.size();
        }
    }

    public boolean hasUnhandledEvent() {
        synchronized (lock) {
            return !tasks.isEmpty();
        }
    }

    public void reset() {
        synchronized (lock) {
            tasks.clear();
            succeededEvents = 0;
            discardedEvents = 0;
        }
    }

}
