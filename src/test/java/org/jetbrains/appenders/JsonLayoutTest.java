/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.appenders;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.jayway.jsonassert.JsonAsserter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;

import static com.jayway.jsonassert.JsonAssert.with;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.spy;

public class JsonLayoutTest {

    @Rule
    public TestName testName = new TestName();

    private ByteArrayOutputStream consoleWriter;
    private JsonLayout consoleLayout;
    private Logger logger;

    @Before
    public void setUp() throws Exception {
        consoleWriter = new ByteArrayOutputStream();

        consoleLayout = new JsonLayout();

        ConsoleAppender<ILoggingEvent> consoleAppender = spy(new ConsoleAppender<ILoggingEvent>());

        // assume SLF4J is bound to logback in the current environment
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        ch.qos.logback.classic.Logger logger = context.getLogger("test");
        consoleAppender.setEncoder(consoleLayout);

        logger.addAppender(consoleAppender);
        logger.setLevel(Level.INFO);

        consoleAppender.setContext(context);
        consoleAppender.start();

        consoleLayout.setContext(context);
        consoleLayout.start();

        context.start();
        consoleAppender.setOutputStream(consoleWriter);

        this.logger = LoggerFactory.getLogger("test");
    }

    @Test
    public void testDefaultFields() throws Exception {
        MDC.put("mdc_key_1", "1");
        MDC.put("mdc_key_2", "2");
        MDC.put("mdc_key_3", "3");
        MDC.put("mdc_key_4", "4.1");

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        RuntimeException exception = new RuntimeException("Hello World Exception");

        logger.error("Hello World", exception);

        System.out.println(consoleWriter.toString());

        JsonAsserter asserter = with(consoleWriter.toString())
            .assertThat("$.exception.message", equalTo(exception.getMessage()))
            .assertThat("$.exception.class", equalTo(exception.getClass().getName()));
        for (StackTraceElement e : exception.getStackTrace()) {
            asserter
                .assertThat("$.exception.stacktrace", containsString(e.getClassName()))
                .assertThat("$.exception.stacktrace", containsString(e.getMethodName()));
        }
        asserter
            .assertThat("$.level", equalTo("ERROR"))
            .assertThat("$.location", nullValue())
            .assertThat("$.logger", equalTo(logger.getName()))
            .assertThat("$.mdc.mdc_key_1", equalTo("1"))
            .assertThat("$.mdc.mdc_key_2", equalTo("2"))
            .assertThat("$.mdc.mdc_key_3", equalTo("3"))
            .assertThat("$.mdc.mdc_key_4", equalTo("4.1"))
            .assertThat("$.message", equalTo("Hello World"))
            .assertThat("$.path", nullValue())
            .assertThat("$.host", equalTo(InetAddress.getLocalHost().getHostName()))
            .assertThat("$.tags", nullValue())
            .assertThat("$.thread", equalTo(Thread.currentThread().getName()))
            .assertThat("$.@timestamp", notNullValue())
            .assertThat("$.@version", equalTo("1"));
    }

    @Test
    public void testIncludeFields() throws Exception {
        consoleLayout.setIncludedFields("logger");
        consoleLayout.start();

        logger.info("Hello World");

        with(consoleWriter.toString())
            .assertThat("$.message", equalTo("Hello World"));
    }

    @Test
    public void testJSONIsValid() throws Exception {
        final StringBuilder message = new StringBuilder("Hello World: ");
        for(int c = Character.MIN_VALUE; c <= Character.MAX_VALUE; c++) {
            message.append((char)c);
        }

        consoleLayout.start();
        logger.info(message.toString());

        with(consoleWriter.toString())
                .assertThat("$.message", startsWith("Hello World: "));
    }

    @Test
    public void testExcludeFields() throws Exception {
        consoleLayout.setExcludedFields("mdc,exception");
        consoleLayout.start();

        MDC.put("mdc_key_1", "mdc_val_1");
        MDC.put("mdc_key_2", "mdc_val_2");

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        RuntimeException exception = new RuntimeException("Hello World Exception");

        logger.error("Hello World", exception);

        with(consoleWriter.toString())
            .assertThat("$.exception", nullValue())
            .assertThat("$.level", equalTo("ERROR"))
            .assertThat("$.logger", equalTo(logger.getName()))
            .assertThat("$.mdc", nullValue())
            .assertThat("$.message", equalTo("Hello World"))
            .assertThat("$.ndc", nullValue())
            .assertThat("$.path", nullValue())
            .assertThat("$.host", equalTo(InetAddress.getLocalHost().getHostName()))
            .assertThat("$.tags", nullValue())
            .assertThat("$.thread", equalTo(Thread.currentThread().getName()))
            .assertThat("$.@timestamp", notNullValue())
            .assertThat("$.@version", equalTo("1"));
    }

    @Test
    public void testAddTags() throws Exception {
        consoleLayout.setTags("json,logstash");
        consoleLayout.start();

        logger.info("Hello World");

        with(consoleWriter.toString()).assertThat("$.tags", hasItems("json", "logstash"));
    }

    @Test
    public void testAddFields() throws Exception {
        consoleLayout.setFields("type:log4j,shipper:logstash");
        consoleLayout.start();

        logger.info("Hello World");

        with(consoleWriter.toString())
            .assertThat("$.type", equalTo("log4j"))
            .assertThat("$.shipper", equalTo("logstash"));
    }

    @Test
    public void testEscape() throws Exception {
        logger.info("H\"e\\l/\nl\ro\u0000W\bo\tr\fl\u0001d");

        with(consoleWriter.toString())
            .assertThat("$.message", equalTo("H\"e\\l/\nl\ro\u0000W\bo\tr\fl\u0001d"));
    }
}
