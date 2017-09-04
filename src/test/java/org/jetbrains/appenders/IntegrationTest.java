package org.jetbrains.appenders;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;
import com.jayway.jsonassert.JsonAssert;
import com.jayway.jsonassert.JsonAsserter;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.internal.matchers.Any;
import org.slf4j.LoggerFactory;

import java.io.*;

public class IntegrationTest {
  @Test
  public void textConsoleTest() throws Exception {
    new TestCase() {
      protected String getXMLConfigurationText() throws Exception {
        return load("integration-test-console.xml");
      }

      protected void doTestImpl() throws Exception {
        LoggerFactory.getLogger("xyz").warn("mock");
      }
    }.doTest();
  }

  @Test
  public void textFile() throws Exception {

    final File logFile = File.createTempFile("aaa", "bbb");
    try {
      new TestCase() {
        protected String getXMLConfigurationText() throws Exception {
          return load("integration-test-file.xml").replace("${LOG_FILE}", logFile.getAbsolutePath());
        }

        protected void doTestImpl() throws Exception {
          LoggerFactory.getLogger("xyz").warn("warn22");
          LoggerFactory.getLogger("xyz").debug("debug22");
          LoggerFactory.getLogger("exception").error("xc", new RuntimeException(new RuntimeException(new RuntimeException())));
        }

        @Override
        protected void doCheck() throws Exception {
          final String log = fileToString(logFile);
          System.out.println("Actual output:\r\n" + log + "\r\n");

          int count = 0;

          for (String line : log.split("[\r\n]+")) {
            if (line.trim().length() == 0) continue;
            //check it is JSON
            JsonAsserter with = JsonAssert.with(line);
            if (count == 0) {
              with
                      .assertEquals("$.level", "WARN")
                      .assertEquals("$.logger", "xyz")
                      .assertEquals("$.message", "warn22")
                      .assertEquals("$.host", "mega-host")
                      .assertThat("$.@timestamp", Any.ANY);
            }
            if (count == 1) {
              with.assertEquals("$.level", "ERROR")
                      .assertEquals("$.logger", "exception")
                      .assertEquals("$.message", "xc")
                      .assertEquals("$.host", "mega-host")
                      .assertThat("$.@timestamp", Any.ANY)
                      .assertEquals("$.exception.message", "java.lang.RuntimeException: java.lang.RuntimeException")
                      .assertThat("$.exception.stacktrace", Any.ANY);
            }

            count++;
          }
        }
      }.doTest();
    } finally {
      Paths.delete(logFile);
    }
  }

  private abstract class TestCase {
    protected abstract String getXMLConfigurationText() throws Exception;

    protected abstract void doTestImpl() throws Exception;

    protected void doCheck() throws Exception {
    }

    final void doTest() throws Exception {
      final String config = getXMLConfigurationText();
      Assert.assertNotNull(config);
      System.out.println("Running on configuration\r\n" + config + "\r\n");

      // assume SLF4J is bound to logback in the current environment
      LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

      JoranConfigurator configurator = new JoranConfigurator();
      configurator.setContext(context);
      // Call context.reset() to clear any previous configuration, e.g. default
      // configuration. For multi-step configuration, omit calling context.reset().
      context.reset();
      configurator.doConfigure(new ByteArrayInputStream(config.getBytes("utf-8")));



      StatusPrinter.printInCaseOfErrorsOrWarnings(context);

      try {
        doTestImpl();
      } finally {
        context.stop();
        context.reset();
      }

      doCheck();
    }
  }


  private String load(final String resourceName) throws IOException {
    final InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName);
    return toString(is);
  }

  private String fileToString(final File file) throws IOException {
    return toString(new FileInputStream(file));
  }

  private String toString(final InputStream is) throws IOException {
    Assert.assertNotNull(is);
    try {
      final ByteArrayOutputStream bos = new ByteArrayOutputStream();
      final BufferedInputStream r = new BufferedInputStream(is);
      while (true) {
        int i = r.read();
        if (i < 0) break;
        bos.write(i);
      }
      return bos.toString("utf-8");
    } finally {
      is.close();
    }
  }
}
