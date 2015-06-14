package org.jetbrains.appenders;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.io.StringWriter;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 */
public class CategoryFilterTest {

  @Rule
  public TestName testName = new TestName();

  private StringWriter consoleWriter;
  private CategoryFilter consoleFilter;

  @Before
  public void setUp() throws Exception {
    consoleWriter = new StringWriter();

    JsonLayout consoleLayout = new JsonLayout();
    consoleLayout.activateOptions();

    ConsoleAppender consoleAppender = spy(new ConsoleAppender());
    doNothing().when(consoleAppender).activateOptions();
    consoleAppender.setWriter(consoleWriter);
    consoleAppender.setLayout(consoleLayout);
    consoleAppender.activateOptions();

    consoleFilter = new CategoryFilter();
    consoleAppender.addFilter(consoleFilter);

    Logger logger = Logger.getRootLogger();
    logger.addAppender(consoleAppender);
    logger.setLevel(Level.ALL);
  }

  @Test
  public void should_not_filter_an_event_by_default() {
    Logger.getLogger(getClass()).info("aaa");

    Assert.assertThat(consoleWriter.toString(), containsString("aaa"));
  }

  @Test
  public void should_filter_an_event_by_logger_name() {
    consoleFilter.setDenyCategory(getClass().getName());
    Logger.getLogger(getClass()).info("aaa");

    Assert.assertThat(consoleWriter.toString(), not(containsString("aaa")));
  }

  @Test
  public void should_not_filter_an_event_by_logger_name() {
    consoleFilter.setDenyCategory(getClass().getName());
    Logger.getLogger("qqq").info("aaa");

    Assert.assertThat(consoleWriter.toString(), containsString("aaa"));
  }

  @Test
  public void should_filter_an_event_by_base_package() {
    consoleFilter.setDenyCategory("org.jetbrains");
    Logger.getLogger(getClass()).info("aaa");

    Assert.assertThat(consoleWriter.toString(), not(containsString("aaa")));
  }

  @Test
  public void should_not_filter_an_event_by_base_package() {
    consoleFilter.setDenyCategory("org.jetbrains");
    Logger.getLogger("qqqq.org.jetbrains").info("aaa");

    Assert.assertThat(consoleWriter.toString(), containsString("aaa"));
  }

  @Test
  public void should_filter_an_event_by_base_package_and_level() {
    consoleFilter.setDenyCategory("org.jetbrains");
    consoleFilter.setMaxDenyLevel(Level.INFO);

    Logger.getLogger(getClass()).debug("bbb");

    Assert.assertThat(consoleWriter.toString(), not(containsString("bbb")));
  }

  @Test
  public void should_not_filter_an_event_by_base_package_and_level() {
    consoleFilter.setDenyCategory("org.jetbrains");
    consoleFilter.setMaxDenyLevel(Level.INFO);

    Logger.getLogger("qqqq.org.jetbrains.appenders").debug("bbb");

    Assert.assertThat(consoleWriter.toString(), containsString("bbb"));
  }

  @Test
  public void should_not_filter_an_event_by_base_package_and_not_level() {
    consoleFilter.setDenyCategory("org.jetbrains");
    consoleFilter.setMaxDenyLevel(Level.INFO);
    Logger.getLogger(getClass()).warn("aaa");

    Assert.assertThat(consoleWriter.toString(), containsString("aaa"));
  }

}
