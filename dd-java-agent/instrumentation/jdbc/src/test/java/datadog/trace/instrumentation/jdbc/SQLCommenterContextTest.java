package datadog.trace.instrumentation.jdbc;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SQLCommenterContextTest {

  @Before
  public void setUp() {
    SQLCommenterContext.setContextMap(null);
  }

  @After
  public void tearDown() {
    SQLCommenterContext.setContextMap(null);
  }

  @Test
  public void testGetCopyOfContextMapEmpty() {
    assertNull(SQLCommenterContext.getCopyOfContextMap());
  }

  @Test
  public void testSetAndGetContextMap() {
    Map<String, String> contextMap = new HashMap<>();
    contextMap.put("key1", "value1");
    contextMap.put("key2", "value2");

    SQLCommenterContext.setContextMap(contextMap);

    Map<String, String> retrievedMap = SQLCommenterContext.getCopyOfContextMap();
    assertNotNull(retrievedMap);
    assertEquals(2, retrievedMap.size());
    assertEquals("value1", retrievedMap.get("key1"));
    assertEquals("value2", retrievedMap.get("key2"));

    // Verify it's a copy (modifications don't affect original)
    retrievedMap.put("key3", "value3");
    Map<String, String> retrievedAgain = SQLCommenterContext.getCopyOfContextMap();
    assertEquals(2, retrievedAgain.size());
    assertNull(retrievedAgain.get("key3"));
  }

  @Test
  public void testSetContextMapMakesInternalCopy() {
    Map<String, String> contextMap = new HashMap<>();
    contextMap.put("key1", "value1");

    SQLCommenterContext.setContextMap(contextMap);

    // Modify the original map
    contextMap.put("key2", "value2");

    // Internal context should not be affected
    Map<String, String> retrievedMap = SQLCommenterContext.getCopyOfContextMap();
    assertEquals(1, retrievedMap.size());
    assertEquals("value1", retrievedMap.get("key1"));
    assertNull(retrievedMap.get("key2"));
  }

  @Test
  public void testSetContextMapNull() {
    Map<String, String> contextMap = new HashMap<>();
    contextMap.put("key1", "value1");
    SQLCommenterContext.setContextMap(contextMap);

    // Verify context is set
    assertNotNull(SQLCommenterContext.getCopyOfContextMap());

    // Clear with null
    SQLCommenterContext.setContextMap(null);
    assertNull(SQLCommenterContext.getCopyOfContextMap());
  }

  @Test
  public void testThreadIsolation() throws Exception {
    Map<String, String> mainContext = new HashMap<>();
    mainContext.put("main_key", "main_value");
    SQLCommenterContext.setContextMap(mainContext);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch latch = new CountDownLatch(2);

    // Start two threads with different contexts
    Future<String> future1 =
        executor.submit(
            () -> {
              try {
                Map<String, String> thread1Context = new HashMap<>();
                thread1Context.put("thread1_key", "thread1_value");
                SQLCommenterContext.setContextMap(thread1Context);

                latch.countDown();
                latch.await(5, TimeUnit.SECONDS);

                // Each thread should only see its own context
                Map<String, String> context = SQLCommenterContext.getCopyOfContextMap();
                assertEquals(1, context.size());
                assertEquals("thread1_value", context.get("thread1_key"));
                assertNull(context.get("thread2_key"));
                assertNull(context.get("main_key")); // Thread local, so main context not visible

                return "thread1_done";
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    Future<String> future2 =
        executor.submit(
            () -> {
              try {
                Map<String, String> thread2Context = new HashMap<>();
                thread2Context.put("thread2_key", "thread2_value");
                SQLCommenterContext.setContextMap(thread2Context);

                latch.countDown();
                latch.await(5, TimeUnit.SECONDS);

                // Each thread should only see its own context
                Map<String, String> context = SQLCommenterContext.getCopyOfContextMap();
                assertEquals(1, context.size());
                assertEquals("thread2_value", context.get("thread2_key"));
                assertNull(context.get("thread1_key"));
                assertNull(context.get("main_key")); // Thread local, so main context not visible

                return "thread2_done";
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    assertEquals("thread1_done", future1.get(10, TimeUnit.SECONDS));
    assertEquals("thread2_done", future2.get(10, TimeUnit.SECONDS));

    // Main thread should still have its context
    Map<String, String> mainContextAfter = SQLCommenterContext.getCopyOfContextMap();
    assertEquals(1, mainContextAfter.size());
    assertEquals("main_value", mainContextAfter.get("main_key"));
    assertNull(mainContextAfter.get("thread1_key"));
    assertNull(mainContextAfter.get("thread2_key"));

    executor.shutdown();
  }

  @Test
  public void testContextPersistence() {
    // Set initial context
    Map<String, String> context1 = new HashMap<>();
    context1.put("key1", "value1");
    SQLCommenterContext.setContextMap(context1);

    // Update context
    Map<String, String> context2 = new HashMap<>();
    context2.put("key2", "value2");
    SQLCommenterContext.setContextMap(context2);

    // Should only have the latest context
    Map<String, String> retrieved = SQLCommenterContext.getCopyOfContextMap();
    assertEquals(1, retrieved.size());
    assertEquals("value2", retrieved.get("key2"));
    assertNull(retrieved.get("key1"));
  }
}
