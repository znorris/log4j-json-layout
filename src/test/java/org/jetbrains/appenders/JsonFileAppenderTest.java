package org.jetbrains.appenders;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 */
public class JsonFileAppenderTest {
  private File home;
  private NextRollingFileAppender appender;

  @Before
  public void before() throws IOException {
    home = File.createTempFile("aaa", "bbb");
    Paths.delete(home);
    //noinspection ResultOfMethodCallIgnored
    home.mkdirs();
    Assert.assertTrue(home.isDirectory());

    appender = new JsonFileAppender();
    appender.setFile(new File(home, "log").getPath());

    Logger.getRootLogger().removeAllAppenders();
    Logger.getRootLogger().addAppender(appender);

    appender.activateOptions();
  }

  @After
  public void after() {
    if (home != null) {
      Paths.delete(home);
    }
  }


  @Test
  public void test_json_appender_smoke() throws IOException {
    final String message = "this is a test message";

    Logger.getLogger(getClass()).warn(message);
    Logger.getRootLogger().removeAllAppenders();

    dumpFiles();

    int off = 0;
    byte[] allData = new byte[8192];
    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(home, "log.1.json")));
    try {
      int x;
      while((x = bis.read(allData, off, allData.length - off)) > 0) {
        off += x;
      }
    } finally {
      bis.close();
    }


    final String text = new String(allData, 0, off, "utf-8");
    System.out.println(text);

    Assert.assertTrue(text.contains(message));
  }


  private Set<String> dumpFiles() {
    System.out.println("Files in the directory: ");
    final Set<String> actual = new TreeSet<String>();
    for (String file : home.list()) {
      actual.add(file);
      System.out.println("  " + file);
    }
    return actual;
  }



}
