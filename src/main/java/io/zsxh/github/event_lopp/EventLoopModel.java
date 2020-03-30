package io.zsxh.github.event_lopp;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

/**
 * leverage asynchronous programming and non-blocking I/O to handle more concurrent connections and use less theads, and
 * use one threading model for asynchronous event processing (the event loop)
 */
public class EventLoopModel {

  public static void main(String[] args) {
    EventLoop eventLoop = new EventLoop();

    // A first thread that dispatches events every second to the event-loop.
    new Thread(() -> {
      for (int n = 0; n < 6; n++) {
        delay(1000);
        eventLoop.dispatch(new EventLoop.Event("tick", n));
      }
      eventLoop.dispatch(new EventLoop.Event("stop", null));
    }).start();

    // A second thread that dispatches 2 events at 2500ms and 3300ms.
    new Thread(() -> {
      delay(2500);
      eventLoop.dispatch(new EventLoop.Event("hello", "beautiful world"));
      delay(800);
      eventLoop.dispatch(new EventLoop.Event("hello", "beautiful universe"));
    }).start();

    // Events dispatched from the main thread.
    eventLoop.dispatch(new EventLoop.Event("hello", "world!"));
    eventLoop.dispatch(new EventLoop.Event("foo", "bar"));
    eventLoop
        // Event handlers defined as Java lambda functions.
        .on("hello", s -> System.out.println("hello " + s))
        .on("tick", n -> System.out.println("tick #" + n)).on("stop", v -> eventLoop.stop()).run();
    System.out.println("Byte!");
  }

  // This method wraps a possibly checked exception into an unchecked exception to avoid polluting
  // the
  // main method code with exception handling logic.
  private static void delay(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static final class EventLoop {
    private final ConcurrentLinkedDeque<Event> events = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, Consumer<Object>> handlers = new ConcurrentHashMap<>();

    // Handler are stored in a map where each key has a handler
    public EventLoop on(String key, Consumer<Object> handler) {
      handlers.put(key, handler);
      return this;
    }

    // Dispatching is pushing events to a queue.
    public void dispatch(Event event) {
      events.add(event);
    }

    public void stop() {
      Thread.currentThread().interrupt();
    }

    public void run() {
      // The event loop looks for event, and finds a handler based on event keys.
      while (!(events.isEmpty() && Thread.interrupted())) {
        if (!events.isEmpty()) {
          Event event = events.pop();
          if (handlers.containsKey(event.key)) {
            handlers.get(event.key).accept(event.data);
          } else {
            System.err.println("No handler for key " + event.key);
          }
        }
      }
    }

    public static final class Event {
      private final String key;
      private final Object data;

      public Event(String key, Object data) {
        this.key = key;
        this.data = data;
      }
    }
  }
}
