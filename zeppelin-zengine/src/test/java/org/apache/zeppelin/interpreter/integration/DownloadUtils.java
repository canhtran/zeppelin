/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.integration;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for downloading spark/flink. This is used for spark/flink integration test.
 */
public class DownloadUtils {
  private static Logger LOGGER = LoggerFactory.getLogger(DownloadUtils.class);

  private static String downloadFolder = System.getProperty("user.home") + "/.cache";

  static {
    try {
      FileUtils.forceMkdir(new File(downloadFolder));
    } catch (IOException e) {
      throw new RuntimeException("Fail to create download folder: " + downloadFolder, e);
    }
  }

  public static String downloadSpark(String sparkVersion, String hadoopVersion) {
    String sparkDownloadFolder = downloadFolder + "/spark";
    File targetSparkHomeFolder =
            new File(sparkDownloadFolder + "/spark-" + sparkVersion + "-bin-hadoop" + hadoopVersion);
    if (targetSparkHomeFolder.exists()) {
      LOGGER.info("Skip to download spark as it is already downloaded.");
      return targetSparkHomeFolder.getAbsolutePath();
    }
    download("spark", sparkVersion, "-bin-hadoop" + hadoopVersion + ".tgz");
    return targetSparkHomeFolder.getAbsolutePath();
  }

  public static String downloadFlink(String flinkVersion, String scalaVersion) {
    String flinkDownloadFolder = downloadFolder + "/flink";
    File targetFlinkHomeFolder = new File(flinkDownloadFolder + "/flink-" + flinkVersion);
    if (targetFlinkHomeFolder.exists()) {
      LOGGER.info("Skip to download flink as it is already downloaded.");
      return targetFlinkHomeFolder.getAbsolutePath();
    }
    download("flink", flinkVersion, "-bin-scala_" + scalaVersion + ".tgz");
    // download other dependencies for running flink with yarn and hive
    try {
      runShellCommand(new String[]{"wget",
              "https://repo1.maven.org/maven2/org/apache/flink/flink-connector-hive_" + scalaVersion + "/"
                      + flinkVersion + "/flink-connector-hive_" + scalaVersion + "-" + flinkVersion + ".jar",
              "-P", targetFlinkHomeFolder + "/lib"});
      runShellCommand(new String[]{"wget",
              "https://repo1.maven.org/maven2/org/apache/flink/flink-hadoop-compatibility_" + scalaVersion + "/"
                      + flinkVersion + "/flink-hadoop-compatibility_" + scalaVersion + "-" + flinkVersion + ".jar",
              "-P", targetFlinkHomeFolder + "/lib"});
      runShellCommand(new String[]{"wget",
              "https://repo1.maven.org/maven2/org/apache/hive/hive-exec/2.3.7/hive-exec-2.3.7.jar",
              "-P", targetFlinkHomeFolder + "/lib"});
      runShellCommand(new String[]{"wget",
              "https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-client-api/3.3.6/hadoop-client-api-3.3.6.jar",
              "-P", targetFlinkHomeFolder + "/lib"});
      runShellCommand(new String[]{"wget",
              "https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-client-runtime/3.3.6/hadoop-client-runtime-3.3.6.jar",
              "-P", targetFlinkHomeFolder + "/lib"});
      runShellCommand(new String[]{"wget",
              "https://repo1.maven.org/maven2/org/apache/flink/flink-table-api-scala_" + scalaVersion + "/"
                      + flinkVersion + "/flink-table-api-scala_" + scalaVersion + "-" + flinkVersion + ".jar",
              "-P", targetFlinkHomeFolder + "/lib"});
      runShellCommand(new String[]{"wget",
              "https://repo1.maven.org/maven2/org/apache/flink/flink-table-api-scala-bridge_" + scalaVersion + "/"
                      + flinkVersion + "/flink-table-api-scala-bridge_" + scalaVersion + "-" + flinkVersion + ".jar",
              "-P", targetFlinkHomeFolder + "/lib"});
      runShellCommand(new String[]{"mv",
              targetFlinkHomeFolder + "/opt/" + "flink-table-planner_" + scalaVersion + "-" + flinkVersion + ".jar",
              targetFlinkHomeFolder + "/lib"});
      runShellCommand(new String[]{"mv",
              targetFlinkHomeFolder + "/lib/" + "flink-table-planner-loader-" + flinkVersion + ".jar",
              targetFlinkHomeFolder + "/opt"});
      if (SemanticVersion.of(flinkVersion).equalsOrNewerThan(SemanticVersion.of("1.16.0"))) {
        runShellCommand(new String[]{"mv",
                targetFlinkHomeFolder + "/opt/" + "flink-sql-client-" + flinkVersion + ".jar",
                targetFlinkHomeFolder + "/lib"});
      }
    } catch (Exception e) {
      throw new RuntimeException("Fail to download jar", e);
    }
    return targetFlinkHomeFolder.getAbsolutePath();
  }

  public static String downloadHadoop(String version) {
    String hadoopDownloadFolder = downloadFolder + "/hadoop";
    File targetHadoopHomeFolder = new File(hadoopDownloadFolder + "/hadoop-" + version);
    if (targetHadoopHomeFolder.exists()) {
      LOGGER.info("Skip to download hadoop as it is already downloaded.");
      return targetHadoopHomeFolder.getAbsolutePath();
    }
    download("hadoop", version, ".tar.gz", "hadoop/core");
    return targetHadoopHomeFolder.getAbsolutePath();
  }

  // Try mirrors first, if fails fallback to apache archive
  private static void download(String project, String version, String postFix, String projectPath) {
    String projectDownloadFolder = downloadFolder + "/" + project;
    try {
      String preferredMirror = IOUtils.toString(new URL("https://www.apache.org/dyn/closer.lua?preferred=true"), StandardCharsets.UTF_8);
      File downloadFile = new File(projectDownloadFolder + "/" + project + "-" + version + postFix);
      String downloadURL = preferredMirror + "/" + projectPath + "/" + project + "-" + version + "/" + project + "-" + version + postFix;
      runShellCommand(new String[]{"wget", downloadURL, "-P", projectDownloadFolder});
      runShellCommand(new String[]{"tar", "-xvf", downloadFile.getAbsolutePath(), "-C", projectDownloadFolder});
    } catch (Exception e) {
      LOGGER.warn("Failed to download " + project + " from mirror site, fallback to use apache archive", e);
      File downloadFile = new File(projectDownloadFolder + "/" + project + "-" + version + postFix);
      String downloadURL =
              "https://archive.apache.org/dist/" + projectPath + "/" + project +"-"
                      + version
                      + "/" + project + "-"
                      + version
                      + postFix;
      try {
        runShellCommand(new String[]{"wget", downloadURL, "-P", projectDownloadFolder});
        runShellCommand(
                new String[]{"tar", "-xvf", downloadFile.getAbsolutePath(), "-C", projectDownloadFolder});
      } catch (Exception ex) {
        throw new RuntimeException("Fail to download " + project + " " + version, ex);
      }
    }
  }

  private static void download(String project, String version, String postFix) {
    download(project, version, postFix, project);
  }

  private static void runShellCommand(String[] commands) throws IOException, InterruptedException {
    LOGGER.info("Starting shell commands: " + StringUtils.join(commands, " "));
    Process process = Runtime.getRuntime().exec(commands);
    StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream());
    StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream());
    errorGobbler.start();
    outputGobbler.start();
    if (process.waitFor() != 0) {
      throw new IOException("Fail to run shell commands: " + StringUtils.join(commands, " "));
    }
    LOGGER.info("Complete shell commands: " + StringUtils.join(commands, " "));
  }

  private static class StreamGobbler extends Thread {
    InputStream is;

    // reads everything from is until empty.
    StreamGobbler(InputStream is) {
      this.is = is;
    }

    @Override
    public void run() {
      try {
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        String line = null;
        long startTime = System.currentTimeMillis();
        while ((line = br.readLine()) != null) {
          // logging per 5 seconds
          if ((System.currentTimeMillis() - startTime) > 5000) {
            LOGGER.info(line);
            startTime = System.currentTimeMillis();
          }
        }
      } catch (IOException ioe) {
        LOGGER.warn("Fail to print shell output", ioe);
      }
    }
  }
}
