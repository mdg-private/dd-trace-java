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
    SQLCommenterContext.clear();
  }

  @After
  public void tearDown() {
    SQLCommenterContext.clear();
  }

  @Test
  public void testPutAndGet() {
    assertNull(SQLCommenterContext.get("test_key"));

    SQLCommenterContext.put("test_key", "test_value");
    assertEquals("test_value", SQLCommenterContext.get("test_key"));

    SQLCommenterContext.put("test_key", 123);
    assertEquals("123", SQLCommenterContext.get("test_key"));

    SQLCommenterContext.put("test_key", null);
    assertNull(SQLCommenterContext.get("test_key"));
  }

  @Test
  public void testRemove() {
    SQLCommenterContext.put("test_key", "test_value");
    assertEquals("test_value", SQLCommenterContext.get("test_key"));

    String removed = SQLCommenterContext.remove("test_key");
    assertEquals("test_value", removed);
    assertNull(SQLCommenterContext.get("test_key"));

    assertNull(SQLCommenterContext.remove("nonexistent"));
  }

  @Test
  public void testClear() {
    SQLCommenterContext.put("key1", "value1");
    SQLCommenterContext.put("key2", "value2");
    assertFalse(SQLCommenterContext.isEmpty());

    SQLCommenterContext.clear();
    assertTrue(SQLCommenterContext.isEmpty());
    assertNull(SQLCommenterContext.get("key1"));
    assertNull(SQLCommenterContext.get("key2"));
  }

  @Test
  public void testIsEmpty() {
    assertTrue(SQLCommenterContext.isEmpty());

    SQLCommenterContext.put("key", "value");
    assertFalse(SQLCommenterContext.isEmpty());

    SQLCommenterContext.clear();
    assertTrue(SQLCommenterContext.isEmpty());
  }

  @Test
  public void testGetCopyOfContextMap() {
    assertNull(SQLCommenterContext.getCopyOfContextMap());

    SQLCommenterContext.put("key1", "value1");
    SQLCommenterContext.put("key2", "value2");

    Map<String, String> contextMap = SQLCommenterContext.getCopyOfContextMap();
    assertNotNull(contextMap);
    assertEquals(2, contextMap.size());
    assertEquals("value1", contextMap.get("key1"));
    assertEquals("value2", contextMap.get("key2"));

    // Verify it's a copy (modifications don't affect original)
    contextMap.put("key3", "value3");
    assertNull(SQLCommenterContext.get("key3"));
  }

  @Test
  public void testSetContextMap() {
    Map<String, String> contextMap = new HashMap<>();
    contextMap.put("key1", "value1");
    contextMap.put("key2", "value2");

    SQLCommenterContext.setContextMap(contextMap);
    assertEquals("value1", SQLCommenterContext.get("key1"));
    assertEquals("value2", SQLCommenterContext.get("key2"));

    // Verify the internal map is a copy
    contextMap.put("key3", "value3");
    assertNull(SQLCommenterContext.get("key3"));

    // Test setting null clears the context
    SQLCommenterContext.setContextMap(null);
    assertTrue(SQLCommenterContext.isEmpty());
  }

  @Test
  public void testWithSQLCommentFieldsMap() throws Exception {
    SQLCommenterContext.put("existing_key", "existing_value");

    Map<String, Object> customFields = new HashMap<>();
    customFields.put("custom_key", "custom_value");
    customFields.put("number_key", 42);
    customFields.put("null_key", null);

    String result =
        SQLCommenterContext.withSQLCommentFields(
            customFields,
            () -> {
              // Inside the block, we should have both existing and custom fields
              assertEquals("existing_value", SQLCommenterContext.get("existing_key"));
              assertEquals("custom_value", SQLCommenterContext.get("custom_key"));
              assertEquals("42", SQLCommenterContext.get("number_key"));
              assertNull(SQLCommenterContext.get("null_key"));

              return "block_executed";
            });

    assertEquals("block_executed", result);

    // After the block, only the original context should remain
    assertEquals("existing_value", SQLCommenterContext.get("existing_key"));
    assertNull(SQLCommenterContext.get("custom_key"));
    assertNull(SQLCommenterContext.get("number_key"));
  }

  @Test
  public void testWithSQLCommentFieldsVarArgs() throws Exception {
    SQLCommenterContext.put("existing_key", "existing_value");

    String result =
        SQLCommenterContext.withSQLCommentFields(
            () -> {
              assertEquals("existing_value", SQLCommenterContext.get("existing_key"));
              assertEquals("custom_value", SQLCommenterContext.get("custom_key"));
              assertEquals("42", SQLCommenterContext.get("number_key"));
              return "block_executed";
            },
            "custom_key",
            "custom_value",
            "number_key",
            42);

    assertEquals("block_executed", result);

    // After the block, only the original context should remain
    assertEquals("existing_value", SQLCommenterContext.get("existing_key"));
    assertNull(SQLCommenterContext.get("custom_key"));
    assertNull(SQLCommenterContext.get("number_key"));
  }

  @Test
  public void testWithSQLCommentFieldsNested() throws Exception {
    String result =
        SQLCommenterContext.withSQLCommentFields(
            () -> {
              SQLCommenterContext.put("level1", "value1");

              return SQLCommenterContext.withSQLCommentFields(
                  () -> {
                    assertEquals("value1", SQLCommenterContext.get("level1"));
                    assertEquals("value2", SQLCommenterContext.get("level2"));
                    return "nested_executed";
                  },
                  "level2",
                  "value2");
            },
            "outer_key",
            "outer_value");

    assertEquals("nested_executed", result);

    // After nested execution, context should be empty
    assertTrue(SQLCommenterContext.isEmpty());
  }

  @Test
  public void testWithSQLCommentFieldsExceptionHandling() {
    SQLCommenterContext.put("existing_key", "existing_value");

    RuntimeException expectedException = new RuntimeException("test exception");

    try {
      SQLCommenterContext.withSQLCommentFields(
          () -> {
            SQLCommenterContext.put("temp_key", "temp_value");
            throw expectedException;
          },
          "custom_key",
          "custom_value");
      fail("Should have thrown exception");
    } catch (Exception e) {
      assertEquals(expectedException, e);
    }

    // Context should be restored even after exception
    assertEquals("existing_value", SQLCommenterContext.get("existing_key"));
    assertNull(SQLCommenterContext.get("temp_key"));
    assertNull(SQLCommenterContext.get("custom_key"));
  }

  @Test
  public void testThreadIsolation() throws Exception {
    SQLCommenterContext.put("main_key", "main_value");

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch latch = new CountDownLatch(2);

    // Start two threads with different contexts
    Future<String> future1 =
        executor.submit(
            () -> {
              try {
                SQLCommenterContext.put("thread1_key", "thread1_value");
                latch.countDown();
                latch.await(5, TimeUnit.SECONDS);

                // Each thread should only see its own context
                assertEquals("thread1_value", SQLCommenterContext.get("thread1_key"));
                assertNull(SQLCommenterContext.get("thread2_key"));
                assertNull(
                    SQLCommenterContext.get(
                        "main_key")); // Thread local, so main context not visible

                return "thread1_done";
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    Future<String> future2 =
        executor.submit(
            () -> {
              try {
                SQLCommenterContext.put("thread2_key", "thread2_value");
                latch.countDown();
                latch.await(5, TimeUnit.SECONDS);

                // Each thread should only see its own context
                assertEquals("thread2_value", SQLCommenterContext.get("thread2_key"));
                assertNull(SQLCommenterContext.get("thread1_key"));
                assertNull(
                    SQLCommenterContext.get(
                        "main_key")); // Thread local, so main context not visible

                return "thread2_done";
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    assertEquals("thread1_done", future1.get(10, TimeUnit.SECONDS));
    assertEquals("thread2_done", future2.get(10, TimeUnit.SECONDS));

    // Main thread should still have its context
    assertEquals("main_value", SQLCommenterContext.get("main_key"));
    assertNull(SQLCommenterContext.get("thread1_key"));
    assertNull(SQLCommenterContext.get("thread2_key"));

    executor.shutdown();
  }

  @Test
  public void testVarArgsValidation() {
    try {
      SQLCommenterContext.withSQLCommentFields(() -> "test", "odd_number_of_args");
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertEquals("Key-value pairs must be provided in pairs", e.getMessage());
    } catch (Exception e) {
      fail("Should have thrown IllegalArgumentException, but got: " + e.getClass().getSimpleName());
    }
  }

  @Test
  public void testPutWithNullKey() {
    try {
      SQLCommenterContext.put(null, "value");
      fail("Should have thrown IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertEquals("Key cannot be null", e.getMessage());
    }
  }
}
