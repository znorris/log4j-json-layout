package org.jetbrains.appenders;

import org.apache.log4j.Level;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;

/**ª
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 *
 */
public class CategoryFilter extends Filter {
  private String myDenyCategoryStartsWith;
  private Level myMaxDenyLevel;

  @Override
  public int decide(final LoggingEvent loggingEvent) {
    if (loggingEvent == null) return NEUTRAL;

    if (categoryIsDenied(loggingEvent) && levelIsDenied(loggingEvent)) {
      return DENY;
    }

    return NEUTRAL;
  }

  private boolean categoryIsDenied(final LoggingEvent loggingEvent) {
    if (myDenyCategoryStartsWith == null) return false;

    final String loggerName = loggingEvent.getLoggerName();
    if (loggerName == null) return false;

    //NOP
    return loggerName.startsWith(myDenyCategoryStartsWith);
  }

  private boolean levelIsDenied(final LoggingEvent loggingEvent) {
    return myMaxDenyLevel == null || myMaxDenyLevel.isGreaterOrEqual(loggingEvent.getLevel());
  }

  public void setDenyCategory(final String denyCategory) {
    if (denyCategory == null) {
      myDenyCategoryStartsWith = null;
    } else {
      myDenyCategoryStartsWith = denyCategory.trim();
    }
  }

  public void setMaxDenyLevel(final Level level) {
    myMaxDenyLevel = level;
  }
}
