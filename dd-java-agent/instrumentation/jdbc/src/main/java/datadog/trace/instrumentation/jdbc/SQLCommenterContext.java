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
}
