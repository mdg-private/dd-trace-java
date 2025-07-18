package datadog.trace.instrumentation.jdbc;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-local context system for custom SQL comment fields. This class provides functionality
 * similar to SLF4J's MDC but specifically for SQL comment injection. External users can add custom
 * key-value pairs that will be included in SQL comments.
 */
public class SQLCommenterContext {

  private static final ThreadLocal<Map<String, String>> contextHolder =
      new ThreadLocal<Map<String, String>>() {
        @Override
        protected Map<String, String> initialValue() {
          return new HashMap<>();
        }
      };

  /**
   * Gets a copy of the current context map.
   *
   * @return a copy of the current context map, or null if no context is set
   */
  public static Map<String, String> getCopyOfContextMap() {
    Map<String, String> contextMap = contextHolder.get();
    return contextMap.isEmpty() ? null : new HashMap<>(contextMap);
  }

  /**
   * Sets the context map for the current thread.
   *
   * @param contextMap the new context map to set
   */
  public static void setContextMap(Map<String, String> contextMap) {
    if (contextMap == null) {
      contextHolder.remove();
    } else {
      contextHolder.set(new HashMap<>(contextMap));
    }
  }

  /**
   * Puts a key-value pair into the current context.
   *
   * @param key the key
   * @param value the value (will be converted to string)
   */
  public static void put(String key, Object value) {
    if (key == null) {
      throw new IllegalArgumentException("Key cannot be null");
    }
    Map<String, String> contextMap = contextHolder.get();
    contextMap.put(key, value != null ? value.toString() : null);
  }

  /**
   * Gets a value from the current context.
   *
   * @param key the key to look up
   * @return the value associated with the key, or null if not found
   */
  public static String get(String key) {
    return contextHolder.get().get(key);
  }

  /**
   * Removes a key from the current context.
   *
   * @param key the key to remove
   * @return the previous value associated with the key, or null if not found
   */
  public static String remove(String key) {
    return contextHolder.get().remove(key);
  }

  /** Clears the current context. */
  public static void clear() {
    contextHolder.remove();
  }

  /**
   * Returns true if the current context is empty.
   *
   * @return true if the current context has no entries
   */
  public static boolean isEmpty() {
    return contextHolder.get().isEmpty();
  }

  /**
   * Executes a block of code with the given context map, restoring the original context afterward.
   *
   * @param contextMap the context map to use during execution
   * @param block the code block to execute
   * @param <T> the return type of the block
   * @return the result of executing the block
   */
  public static <T> T withSQLCommentFields(
      Map<String, Object> contextMap, SQLCommenterBlock<T> block) throws Exception {
    Map<String, String> oldContextMap = getCopyOfContextMap();
    try {
      Map<String, String> newContextMap =
          new HashMap<>(oldContextMap != null ? oldContextMap : new HashMap<>());
      if (contextMap != null) {
        contextMap.forEach(
            (key, value) -> newContextMap.put(key, value != null ? value.toString() : null));
      }
      setContextMap(newContextMap);
      return block.execute();
    } finally {
      setContextMap(oldContextMap);
    }
  }

  /**
   * Executes a block of code with the given key-value pairs, restoring the original context
   * afterward.
   *
   * @param block the code block to execute
   * @param keyValuePairs alternating key-value pairs to add to the context
   * @param <T> the return type of the block
   * @return the result of executing the block
   */
  @SuppressWarnings("unchecked")
  public static <T> T withSQLCommentFields(SQLCommenterBlock<T> block, Object... keyValuePairs)
      throws Exception {
    if (keyValuePairs.length % 2 != 0) {
      throw new IllegalArgumentException("Key-value pairs must be provided in pairs");
    }

    Map<String, Object> contextMap = new HashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      String key = keyValuePairs[i].toString();
      Object value = keyValuePairs[i + 1];
      contextMap.put(key, value);
    }

    return withSQLCommentFields(contextMap, block);
  }

  /**
   * Functional interface for code blocks that can be executed with a SQL comment context.
   *
   * @param <T> the return type of the block
   */
  @FunctionalInterface
  public interface SQLCommenterBlock<T> {
    T execute() throws Exception;
  }
}
