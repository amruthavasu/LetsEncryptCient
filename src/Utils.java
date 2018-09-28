

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class Utils {
  /**
   * Renames a file to desFile name.
   * @param srcFile source file name
   * @param destFile destination file name
   */
  public static void renameFile(String srcFile, String destFile) {
    File source = new File(srcFile);
    File destination = new File(destFile);
    source.renameTo(destination);
  }

  /**
   * copies a file to destination path.
   * @param srcFile source file path
   * @param desFile destination file path
   */
  public static void copyFiles(String srcFile, String desFile) {
    /*System.out.println("Source: " + srcFile);
     * System.out.println("Destination: " + desFile);*/
    File source = new File(srcFile);
    File destination = new File(desFile);
    try {
      Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.COPY_ATTRIBUTES,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException ioe) {
      // TODO Auto-generated catch block 
      ioe.printStackTrace();
      System.exit(0);
    }
  }
}
