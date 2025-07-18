import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.bootstrap.instrumentation.api.AgentSpan
import datadog.trace.bootstrap.instrumentation.api.AgentTracer
import datadog.trace.bootstrap.instrumentation.api.Tags

import datadog.trace.instrumentation.jdbc.SQLCommenter
import datadog.trace.instrumentation.jdbc.SQLCommenterContext

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class SQLCommenterTest extends AgentTestRunner {

  def "test find first word"() {
    setup:

    when:
    String word = SQLCommenter.getFirstWord(sql)

    then:
    word == firstWord

    where:
    sql               | firstWord
    "SELECT *"        | "SELECT"
    "  { "            | "{"
    "{"               | "{"
    "{call"           | "{call"
    "{ call"          | "{"
    "CALL ( ? )"      | "CALL"
    ""                | ""
    "   "             | ""
  }


  def "test encode Sql Comment"() {
    setup:
    injectSysConfig("dd.service", ddService)
    injectSysConfig("dd.env", ddEnv)
    injectSysConfig("dd.version", ddVersion)

    when:
    String sqlWithComment = ""
    if (injectTrace) {
      sqlWithComment = SQLCommenter.inject(query, dbService, dbType, host, dbName, traceParent, true, appendComment)
    } else if (appendComment) {
      sqlWithComment = SQLCommenter.append(query, dbService, dbType, host, dbName)
    }
    else {
      sqlWithComment = SQLCommenter.prepend(query, dbService, dbType, host, dbName)
    }


    then:
    sqlWithComment == expected

    where:
    query                                                                                                         | ddService      | ddEnv  | dbService    | dbType     | host | dbName | ddVersion     | injectTrace | appendComment | traceParent                                               | expected
    "SELECT * FROM foo"                                                                                           | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * FROM foo"                                                                                           | "SqlCommenter" | "Test" | "my-service" | "postgres" | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "{call dogshelterProc(?, ?)}"                                                                                 | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "{call dogshelterProc(?, ?)} /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "{call dogshelterProc(?, ?)}"                                                                                 | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | false         | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "{call dogshelterProc(?, ?)} /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "{call dogshelterProc(?, ?)}"                                                                                 | "SqlCommenter" | "Test" | "my-service" | "postgres" | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "{call dogshelterProc(?, ?)}"
    "CALL dogshelterProc(?, ?)"                                                                                   | "SqlCommenter" | "Test" | "my-service" | "postgres" | "h"  | "n"    | "TestVersion" | true        | false         | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "CALL dogshelterProc(?, ?) /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "CALL dogshelterProc(?, ?)"                                                                                   | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | false         | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "CALL dogshelterProc(?, ?) /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * FROM foo"                                                                                           | ""             | "Test" | ""           | "mysql"    | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "SELECT * FROM foo /*ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * FROM foo"                                                                                           | ""             | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "SELECT * FROM foo /*dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * FROM foo"                                                                                           | "SqlCommenter" | "Test" | "my-service" | "mysql"    | ""   | ""     | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * FROM foo"                                                                                           | ""             | "Test" | ""           | ""         | "h"  | "n"    | ""            | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "SELECT * FROM foo /*ddh='h',dddb='n',dde='Test',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * FROM foo"                                                                                           | ""             | ""     | ""           | ""         | "h"  | "n"    | ""            | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "SELECT * FROM foo /*ddh='h',dddb='n',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * FROM foo"                                                                                           | ""             | ""     | ""           | ""         | ""   | ""     | ""            | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "SELECT * FROM foo /*traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * from FOO -- test query"                                                                             | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-01" | "SELECT * from FOO -- test query /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/"
    "SELECT /* customer-comment */ * FROM foo"                                                                    | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-01" | "SELECT /* customer-comment */ * FROM foo /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/"
    "SELECT * FROM foo"                                                                                           | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | true          | null                                                      | "SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion'*/"
    "SELECT /* customer-comment */ * FROM foo"                                                                    | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | true          | null                                                      | "SELECT /* customer-comment */ * FROM foo /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion'*/"
    "SELECT * from FOO -- test query"                                                                             | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | true          | null                                                      | "SELECT * from FOO -- test query /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion'*/"
    ""                                                                                                            | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | ""
    "   "                                                                                                         | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-01" | "    /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/"
    ""                                                                                                            | "SqlCommenter" | "Test" | "postgres"   | "mysql"    | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | ""
    "   "                                                                                                         | "SqlCommenter" | "Test" | "postgres"   | "mysql"    | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-01" | "    /*ddps='SqlCommenter',dddbs='postgres',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/"
    "SELECT * FROM foo /*dddbs='my-service',ddh='h',dddb='n',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/" | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | true          | null                                                      | "SELECT * FROM foo /*dddbs='my-service',ddh='h',dddb='n',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/"
    "SELECT * FROM foo /*ddh='h',dddb='n',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/"                    | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | true          | null                                                      | "SELECT * FROM foo /*ddh='h',dddb='n',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/"
    "SELECT * FROM foo /*dddb='n',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/"                            | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | true          | null                                                      | "SELECT * FROM foo /*dddb='n',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/"
    "SELECT * FROM foo /*dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/"                                     | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | true          | null                                                      | "SELECT * FROM foo /*dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/"
    "SELECT * FROM foo /*ddps='SqlCommenter',ddpv='TestVersion'*/"                                                | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | true          | null                                                      | "SELECT * FROM foo /*ddps='SqlCommenter',ddpv='TestVersion'*/"
    "SELECT * FROM foo /*ddpv='TestVersion'*/"                                                                    | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | true          | null                                                      | "SELECT * FROM foo /*ddpv='TestVersion'*/"
    "/*ddjk its a customer */ SELECT * FROM foo"                                                                  | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | true          | null                                                      | "/*ddjk its a customer */ SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion'*/"
    "SELECT * FROM foo /*traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/"                 | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | true          | null                                                      | "SELECT * FROM foo /*traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/"
    "/*customer-comment*/ SELECT * FROM foo"                                                                      | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | true          | null                                                      | "/*customer-comment*/ SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion'*/"
    "/*traceparent"                                                                                               | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | true          | null                                                      | "/*traceparent /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion'*/"
    "SELECT * FROM foo"                                                                                           | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | false         | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "/*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/ SELECT * FROM foo"
    "SELECT * FROM foo"                                                                                           | ""             | "Test" | ""           | "mysql"    | "h"  | "n"    | "TestVersion" | true        | false         | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "/*ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/ SELECT * FROM foo"
    "SELECT * FROM foo"                                                                                           | ""             | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | false         | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "/*dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/ SELECT * FROM foo"
    "SELECT * FROM foo"                                                                                           | ""             | "Test" | ""           | ""         | "h"  | "n"    | ""            | true        | false         | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "/*ddh='h',dddb='n',dde='Test',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/ SELECT * FROM foo"
    "SELECT * FROM foo"                                                                                           | ""             | ""     | ""           | ""         | ""   | ""     | ""            | true        | false         | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "/*traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/ SELECT * FROM foo"
    "SELECT * from FOO -- test query"                                                                             | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | false         | "00-00000000000000007fffffffffffffff-000000024cb016ea-01" | "/*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/ SELECT * from FOO -- test query"
    "SELECT /* customer-comment */ * FROM foo"                                                                    | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | false         | "00-00000000000000007fffffffffffffff-000000024cb016ea-01" | "/*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/ SELECT /* customer-comment */ * FROM foo"
    "SELECT * FROM foo"                                                                                           | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | false         | null                                                      | "/*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion'*/ SELECT * FROM foo"
    "SELECT /* customer-comment */ * FROM foo"                                                                    | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | false         | null                                                      | "/*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion'*/ SELECT /* customer-comment */ * FROM foo"
    "SELECT * from FOO -- test query"                                                                             | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | false         | null                                                      | "/*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion'*/ SELECT * from FOO -- test query"
    ""                                                                                                            | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | false         | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | ""
    "   "                                                                                                         | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | false         | "00-00000000000000007fffffffffffffff-000000024cb016ea-01" | "/*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/    "
    "/*dddbs='my-service',ddh='h',dddb='n',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo" | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | false         | null                                                      | "/*dddbs='my-service',ddh='h',dddb='n',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"
    "/*ddh='h',dddb='n',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"                    | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | false         | null                                                      | "/*ddh='h',dddb='n',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"
    "/*dddb='n',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"                            | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | false         | null                                                      | "/*dddb='n',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"
    "/*dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"                                     | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | false         | null                                                      | "/*dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"
    "/*ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"                                                | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | false         | null                                                      | "/*ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"
    "/*ddpv='TestVersion'*/ SELECT * FROM foo"                                                                    | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | false         | null                                                      | "/*ddpv='TestVersion'*/ SELECT * FROM foo"
    "/*ddjk its a customer */ SELECT * FROM foo"                                                                  | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | false         | null                                                      | "/*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion'*/ /*ddjk its a customer */ SELECT * FROM foo"
    "/*traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/ SELECT * FROM foo"                 | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | false         | null                                                      | "/*traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/ SELECT * FROM foo"
    "/*customer-comment*/ SELECT * FROM foo"                                                                      | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | false         | null                                                      | "/*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion'*/ /*customer-comment*/ SELECT * FROM foo"
    "/*traceparent"                                                                                               | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | false       | false         | null                                                      | "/*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion'*/ /*traceparent"
    "SELECT /*+ SeqScan(foo) */ * FROM foo"                                                                       | "SqlCommenter" | "Test" | "my-service" | "postgres" | "h"  | "n" | "TestVersion"    | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" |  "SELECT /*+ SeqScan(foo) */ * FROM foo /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "/*+ SeqScan(foo) */ SELECT * FROM foo"                                                                       | "SqlCommenter" | "Test" | "my-service" | "postgres" | "h"  | "n" | "TestVersion"    | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" |  "/*+ SeqScan(foo) */ SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
  }

  def "test encode Sql Comment with peer service"() {
    setup:
    injectSysConfig("dd.service", ddService)
    injectSysConfig("dd.env", ddEnv)
    injectSysConfig("dd.version", ddVersion)

    when:
    String sqlWithComment = ""
    runUnderTrace("testTrace"){
      AgentSpan currSpan = AgentTracer.activeSpan()
      currSpan.setTag(Tags.PEER_SERVICE, peerService)

      if (injectTrace) {
        sqlWithComment = SQLCommenter.inject(query, dbService, dbType, host, dbName, traceParent, true, appendComment)
      }
      else if (appendComment) {
        sqlWithComment = SQLCommenter.append(query, dbService, dbType, host, dbName)
      }
      else {
        sqlWithComment = SQLCommenter.prepend(query, dbService, dbType, host, dbName)
      }
    }

    then:
    sqlWithComment == expected

    where:
    query                                                                                                         | ddService      | ddEnv  | dbService    | dbType     | host | dbName | ddVersion     | injectTrace | appendComment | traceParent                                               | peerService | expected
    "SELECT * FROM foo"                                                                                           | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | ""          | "SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * FROM foo"                                                                                           | "SqlCommenter" | "Test" | "my-service" | "postgres" | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | ""          | "SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * FROM foo"                                                                                           | "SqlCommenter" | "Test" | "my-service" | "mysql"    | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "testPeer" | "SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',ddprs='testPeer',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * FROM foo"                                                                                           | "SqlCommenter" | "Test" | "my-service" | "postgres" | "h"  | "n"    | "TestVersion" | true        | true          | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "testPeer" | "SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',ddh='h',dddb='n',ddprs='testPeer',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
  }

  def "test SQL comment injection with custom fields"() {
    setup:
    injectSysConfig("dd.service", "SqlCommenter")
    injectSysConfig("dd.env", "Test")
    injectSysConfig("dd.version", "TestVersion")

    when:
    String sqlWithComment = ""

    // Set up custom fields in context
    SQLCommenterContext.put("tenant_id", "abc123")
    SQLCommenterContext.put("request_id", "req-456")
    SQLCommenterContext.put("user_id", 789)

    sqlWithComment = SQLCommenter.inject(
      "SELECT * FROM foo",
      "my-service",
      "mysql",
      "h",
      "n",
      "00-00000000000000007fffffffffffffff-000000024cb016ea-00",
      true,
      true
      )

    SQLCommenterContext.clear()

    then:
    // Should contain all standard fields plus custom fields
    sqlWithComment.contains("ddps='SqlCommenter'")
    sqlWithComment.contains("dddbs='my-service'")
    sqlWithComment.contains("ddh='h'")
    sqlWithComment.contains("dddb='n'")
    sqlWithComment.contains("dde='Test'")
    sqlWithComment.contains("ddpv='TestVersion'")
    sqlWithComment.contains("traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'")
    sqlWithComment.contains("tenant_id='abc123'")
    sqlWithComment.contains("request_id='req-456'")
    sqlWithComment.contains("user_id='789'")
    sqlWithComment.startsWith("SELECT * FROM foo /*")
    sqlWithComment.endsWith("*/")
  }

  def "test SQL comment injection with custom fields using withSQLCommentFields"() {
    setup:
    injectSysConfig("dd.service", "SqlCommenter")
    injectSysConfig("dd.env", "Test")
    injectSysConfig("dd.version", "TestVersion")

    when:
    String sqlWithComment = SQLCommenterContext.withSQLCommentFields(
      ["tenant_id": "abc123", "request_id": "req-456"], {
        return SQLCommenter.inject(
          "SELECT * FROM foo",
          "my-service",
          "mysql",
          "h",
          "n",
          "00-00000000000000007fffffffffffffff-000000024cb016ea-00",
          true,
          true
          )
      }
      )

    then:
    // Should contain all standard fields plus custom fields
    sqlWithComment.contains("ddps='SqlCommenter'")
    sqlWithComment.contains("dddbs='my-service'")
    sqlWithComment.contains("tenant_id='abc123'")
    sqlWithComment.contains("request_id='req-456'")
    sqlWithComment.startsWith("SELECT * FROM foo /*")
    sqlWithComment.endsWith("*/")

    // Context should be cleared after the block
    SQLCommenterContext.isEmpty()
  }

  def "test SQL comment injection with empty custom fields"() {
    setup:
    injectSysConfig("dd.service", "SqlCommenter")
    injectSysConfig("dd.env", "Test")
    injectSysConfig("dd.version", "TestVersion")

    when:
    String sqlWithComment = SQLCommenter.inject(
      "SELECT * FROM foo",
      "my-service",
      "mysql",
      "h",
      "n",
      "00-00000000000000007fffffffffffffff-000000024cb016ea-00",
      true,
      true
      )

    then:
    // Should contain only standard fields
    sqlWithComment.contains("ddps='SqlCommenter'")
    sqlWithComment.contains("dddbs='my-service'")
    sqlWithComment.contains("ddh='h'")
    sqlWithComment.contains("dddb='n'")
    sqlWithComment.contains("dde='Test'")
    sqlWithComment.contains("ddpv='TestVersion'")
    sqlWithComment.contains("traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'")
    sqlWithComment.startsWith("SELECT * FROM foo /*")
    sqlWithComment.endsWith("*/")
  }

  def "test SQL comment injection with custom fields containing special characters"() {
    setup:
    injectSysConfig("dd.service", "SqlCommenter")
    injectSysConfig("dd.env", "Test")
    injectSysConfig("dd.version", "TestVersion")

    when:
    String sqlWithComment = ""

    // Set up custom fields with special characters that need URL encoding
    SQLCommenterContext.put("special_key", "value with spaces")
    SQLCommenterContext.put("unicode_key", "value_with_émojis_🎉")
    SQLCommenterContext.put("symbols_key", "value&with=symbols")

    sqlWithComment = SQLCommenter.inject(
      "SELECT * FROM foo",
      "my-service",
      "mysql",
      "h",
      "n",
      null,
      false,
      true
      )

    SQLCommenterContext.clear()

    then:
    // Should contain URL-encoded custom fields (URLEncoder uses + for spaces)
    sqlWithComment.contains("special_key='value+with+spaces'")
    sqlWithComment.contains("unicode_key=")  // Should be URL encoded
    sqlWithComment.contains("symbols_key='value%26with%3Dsymbols'")
    sqlWithComment.startsWith("SELECT * FROM foo /*")
    sqlWithComment.endsWith("*/")
  }

  def "test SQL comment injection with null and empty custom field values"() {
    setup:
    injectSysConfig("dd.service", "SqlCommenter")
    injectSysConfig("dd.env", "Test")
    injectSysConfig("dd.version", "TestVersion")

    when:
    String sqlWithComment = ""

    // Set up custom fields with null and empty values
    SQLCommenterContext.put("null_key", null)
    SQLCommenterContext.put("empty_key", "")
    SQLCommenterContext.put("valid_key", "valid_value")

    sqlWithComment = SQLCommenter.inject(
      "SELECT * FROM foo",
      "my-service",
      "mysql",
      "h",
      "n",
      null,
      false,
      true
      )

    SQLCommenterContext.clear()

    then:
    // Should not contain null or empty fields, but should contain valid field
    !sqlWithComment.contains("null_key")
    !sqlWithComment.contains("empty_key")
    sqlWithComment.contains("valid_key='valid_value'")
    sqlWithComment.startsWith("SELECT * FROM foo /*")
    sqlWithComment.endsWith("*/")
  }
}
