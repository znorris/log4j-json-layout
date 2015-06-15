package org.jetbrains.appenders;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 */
public class Paths {
  public static Set<File> delete(File fileOrDirectory) {
    Set<File> visited = new HashSet<File>();
    Set<File> removed = new HashSet<File>();
    LinkedList<File> toRemove = new LinkedList<File>();
    toRemove.add(fileOrDirectory);

    while (!toRemove.isEmpty()) {
      File next = toRemove.peek();

      if (visited.add(next)) {
        File[] files = next.listFiles();
        if (files != null) {
          boolean hasSomethingToRemove = false;
          for (File f : files) {
            if (visited.contains(f)) {
              continue; // already tried to remove
            }
            toRemove.addFirst(f);
            hasSomethingToRemove = true;
          }

          if (files.length > 0 && hasSomethingToRemove) {
            continue;
          }

          if (files.length > 0) {
            toRemove.remove(next);
            continue;  // directory has non removable files (we already tried to remove them)
          }
        }
      }

      boolean fileRemoved = false;
      for (int i = 0; i < 10; i++) {
        if (next.delete() || !next.exists()) {
          removed.add(next);
          fileRemoved = true;
          break;
        }
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          //NOP
        }
      }

      toRemove.remove(next);

      if (!fileRemoved) {
        File parentFile = next.getParentFile();
        while (parentFile != null && toRemove.remove(parentFile)) {
          parentFile = parentFile.getParentFile(); // remove all parent files from queue
        }
      }
    }

    return removed;
  }


}
