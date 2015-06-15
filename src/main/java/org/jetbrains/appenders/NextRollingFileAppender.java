package org.jetbrains.appenders;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.helpers.CountingQuietWriter;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.spi.LoggingEvent;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Writer;
import java.util.*;

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 * This class partially duplicated code from
 * {@link org.apache.log4j.RollingFileAppender}
 * but provides another scenario.
 *
 * The log files are generated in the following sequence:
 *  log.txt
 *  log.1.txt
 *  ...
 *  log.N.txt
 *
 *  The appender cleanups the older files, preserving
 *  {@link #maxBackupIndex} number of files.
 *
 *  After a number of re-starts it may turn out we
 *  write to a file #m, but there are files #m+100.
 *  This will be resolved eventually
 *
 */
public class NextRollingFileAppender extends FileAppender {
  /**
   * The default maximum file size is 10MB.
   */
  protected long maxFileSize = 10 * 1024 * 1024;

  /**
   * There is one backup file by default.
   */
  protected int maxBackupIndex = 10;

  private long nextRollover = 0;

  private String fileExtension = "";

  public String getFileExtension() {
    return fileExtension;
  }

  public void setFileExtension(String fileExtension) {
    this.fileExtension = fileExtension;
  }

  /**
   * The default constructor simply calls its {@link
   * FileAppender#FileAppender parents constructor}.
   */
  public NextRollingFileAppender() {
    super();
  }

  /**
   * Instantiate a RollingFileAppender and open the file designated by
   * <code>filename</code>. The opened filename will become the ouput
   * destination for this appender.
   * <p/>
   * <p>If the <code>append</code> parameter is true, the file will be
   * appended to. Otherwise, the file desginated by
   * <code>filename</code> will be truncated before being opened.
   */
  public NextRollingFileAppender(Layout layout, String filename, boolean append) throws IOException {
    super(layout, filename, append);
  }

  /**
   * Instantiate a FileAppender and open the file designated by
   * <code>filename</code>. The opened filename will become the output
   * destination for this appender.
   * <p/>
   * <p>The file will be appended to.
   */
  public NextRollingFileAppender(Layout layout, String filename) throws IOException {
    super(layout, filename);
  }

  /**
   * Returns the value of the <b>MaxBackupIndex</b> option.
   */
  public int getMaxBackupIndex() {
    return maxBackupIndex;
  }

  /**
   * Get the maximum size that the output file is allowed to reach
   * before being rolled over to backup files.
   *
   * @since 1.1
   */
  public long getMaximumFileSize() {
    return maxFileSize;
  }

  private String realFileName = null;
  private int myCurrentFileId = 1;
  private final Set<File> myPendingFiles = new HashSet<File>();
  private File myWritingFile = null;

  public // synchronization not necessary since doAppend is already synced
  void rollOver() {
    if (qw != null) {
      long size = ((CountingQuietWriter) qw).getCount();
      LogLog.debug("rolling over count=" + size);
      //   if operation fails, do not roll again until
      //      maxFileSize more bytes are written
      nextRollover = size + maxFileSize;
    }
    LogLog.debug("maxBackupIndex=" + maxBackupIndex);

    if (realFileName == null) {
      // fileName may be altered with .X,
      // we need to keep original one somehow
      realFileName = fileName;
    }

    ///clean all pending files
    {
      final LinkedList<File> existingFiles = new LinkedList<File>(myPendingFiles);
      Collections.sort(existingFiles, new Comparator<File>() {
        public int compare(File o1, File o2) {
          final long t1 = o1.lastModified();
          final long t2 = o2.lastModified();
          return (t1 < t2) ? -1 : ((t1 == t2) ? 0 : 1);
        }
      });

      while (existingFiles.size() >= Math.max(1, maxBackupIndex - 1)) {
        final File file = existingFiles.removeFirst();
        myPendingFiles.remove(file);

        //noinspection ResultOfMethodCallIgnored
        file.delete();
      }
    }

    int failures = 0;
    for(;;) {
      final File nextLogFile = new File(realFileName + "." + myCurrentFileId + fileExtension);
      if (nextLogFile.exists()) {
        myPendingFiles.add(nextLogFile);

        myCurrentFileId++;
        continue;
      }

      try {
        final File prevFile = myWritingFile;

        super.setFile(nextLogFile.getPath(), false, bufferedIO, bufferSize);
        nextRollover = maxFileSize;


        myWritingFile = nextLogFile;
        if (prevFile != null) {
          myPendingFiles.add(prevFile);
        }

        break;
      } catch (IOException e) {
        if (e instanceof InterruptedIOException) {
          Thread.currentThread().interrupt();
        }
        LogLog.error("setFile(" + realFileName + ", true) call failed.", e);
        if (failures ++ > 5) {
          LogLog.error("Failed to select next file within 5 attempts. Gave it up");
          return;
        }
      }
    }

    if (myCurrentFileId >= maxBackupIndex * 2 + 1) {
      myCurrentFileId = 1;
    }

  }

  public
  synchronized void setFile(String fileName, boolean append, boolean bufferedIO, int bufferSize) throws IOException {
    realFileName = null;
    rollOver();
  }

  /**
   * Set the maximum number of backup files to keep around.
   * <p/>
   * <p>The <b>MaxBackupIndex</b> option determines how many backup
   * files are kept before the oldest is erased. This option takes
   * a positive integer value. If set to zero, then there will be no
   * backup files and the log file will be truncated when it reaches
   * <code>MaxFileSize</code>.
   */
  public void setMaxBackupIndex(int maxBackups) {
    this.maxBackupIndex = maxBackups;
  }

  /**
   * Set the maximum size that the output file is allowed to reach
   * before being rolled over to backup files.
   * <p/>
   * <p>This method is equivalent to {@link #setMaxFileSize} except
   * that it is required for differentiating the setter taking a
   * <code>long</code> argument from the setter taking a
   * <code>String</code> argument by the JavaBeans {@link
   * java.beans.Introspector Introspector}.
   *
   * @see #setMaxFileSize(String)
   */
  public void setMaximumFileSize(long maxFileSize) {
    this.maxFileSize = maxFileSize;
  }


  /**
   * Set the maximum size that the output file is allowed to reach
   * before being rolled over to backup files.
   * <p/>
   * <p>In configuration files, the <b>MaxFileSize</b> option takes an
   * long integer in the range 0 - 2^63. You can specify the value
   * with the suffixes "KB", "MB" or "GB" so that the integer is
   * interpreted being expressed respectively in kilobytes, megabytes
   * or gigabytes. For example, the value "10KB" will be interpreted
   * as 10240.
   */
  public void setMaxFileSize(String value) {
    maxFileSize = OptionConverter.toFileSize(value, maxFileSize + 1);
  }

  protected void setQWForFiles(Writer writer) {
    this.qw = new CountingQuietWriter(writer, errorHandler);
  }

  /**
   * This method differentiates RollingFileAppender from its super
   * class.
   *
   * @since 0.9.0
   */
  protected void subAppend(LoggingEvent event) {
    super.subAppend(event);
    if (fileName != null && qw != null) {
      long size = ((CountingQuietWriter) qw).getCount();
      if (size >= maxFileSize && size >= nextRollover) {
        rollOver();
      }
    }
  }

}
