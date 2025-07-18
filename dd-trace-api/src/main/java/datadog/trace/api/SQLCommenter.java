package datadog.trace.api;

import java.util.Map;

/**
 * Public API for accessing SQL comment context managed by the Datadog Java tracer. This API
 * provides essential primitives for custom context management integration.
 *
 * <p>This API only works when the dd-trace-java agent is loaded. If the agent is not present, these
 * methods will have no effect. Applications should handle the optional dependency by checking for
 * class availability before calling these methods.
 *
 * <p>Usage example:
 *
 * <pre>
 * // Get current context
 * Map<String, String> currentContext = SQLCommenter.getCopyOfContextMap();
 *
 * // Merge with custom fields and set new context
 * Map<String, String> newContext = new HashMap<>(currentContext != null ? currentContext : Map.of());
 * newContext.put("tenant_id", "abc123");
 * newContext.put("request_id", "xyz789");
 * SQLCommenter.setContextMap(newContext);
 *
 * // Execute database operations...
 *
 * // Restore original context
 * SQLCommenter.setContextMap(currentContext);
 * </pre>
 */
public class SQLCommenter {

  /**
   * Gets a copy of the current thread's SQL comment context map.
   *
   * @return a copy of the current context map, or null if empty
   */
  public static Map<String, String> getCopyOfContextMap() {
    return getContext().getCopyOfContextMap();
  }

  /**
   * Sets the context map for the current thread.
   *
   * @param contextMap the new context map to set, or null to clear
   */
  public static void setContextMap(Map<String, String> contextMap) {
    getContext().setContextMap(contextMap);
  }

  // Direct access to the context
  private static SQLCommenterContextAccess getContext() {
    return SQLCommenterContextHolder.INSTANCE;
  }

  private static class SQLCommenterContextHolder {
    private static final SQLCommenterContextAccess INSTANCE = createInstance();

    private static SQLCommenterContextAccess createInstance() {
      try {
        Class<?> contextClass =
            Class.forName("datadog.trace.instrumentation.jdbc.SQLCommenterContext");
        return new DirectSQLCommenterContextAccess(contextClass);
      } catch (ClassNotFoundException | LinkageError e) {
        // This should not happen if the dd-trace-java agent is properly loaded
        throw new RuntimeException(
            "SQLCommenterContext is not available. Ensure dd-trace-java agent is loaded.", e);
      }
    }
  }

  // Direct access interface
  private interface SQLCommenterContextAccess {
    Map<String, String> getCopyOfContextMap();

    void setContextMap(Map<String, String> contextMap);
  }

  private static class DirectSQLCommenterContextAccess implements SQLCommenterContextAccess {
    private final Class<?> contextClass;

    public DirectSQLCommenterContextAccess(Class<?> contextClass) {
      this.contextClass = contextClass;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> getCopyOfContextMap() {
      try {
        return (Map<String, String>) contextClass.getMethod("getCopyOfContextMap").invoke(null);
      } catch (Exception e) {
        throw new RuntimeException("Failed to get copy of SQL comment context map", e);
      }
    }

    @Override
    public void setContextMap(Map<String, String> contextMap) {
      try {
        contextClass.getMethod("setContextMap", Map.class).invoke(null, contextMap);
      } catch (Exception e) {
        throw new RuntimeException("Failed to set SQL comment context map", e);
      }
    }
  }
}
