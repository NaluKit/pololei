/*
 * Copyright © 2026 Frank Hossfeld
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nalukit.pololei;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against the packaged JAR (failsafe, phase integration-test): a minimal Java program uses
 * pololei on the module path, with the published module name, and on the classpath.
 */
class ModuleNameIT {

  private static final String MAIN = """
                                     package app;

                                     import io.github.nalukit.pololei.Iban;
                                     import io.github.nalukit.pololei.IbanValidator;
                                     import io.github.nalukit.pololei.NationalCheckMode;

                                     public class Main {
                                       public static void main(String[] args) {
                                         Iban iban = Iban.parse("FR1420041010050500013M02606");
                                         boolean national = IbanValidator.standard()
                                                                         .nationalCheck(NationalCheckMode.REQUIRED)
                                                                         .isValid(iban.toString());
                                         System.out.print(iban.toPrintFormat() + " " + national + " " + Main.class.getModule().getName() + " " + Iban.class.getModule().getName());
                                       }
                                     }
                                     """;

  private static Path   jar;
  private static String module;

  @BeforeAll
  static void packagedJar() {
    jar    = Path.of(System.getProperty("pololei.jar"));
    module = System.getProperty("pololei.module");
    assertTrue(Files.isRegularFile(jar), "missing " + jar);
  }

  @Test
  void describeModule() {
    String description = run("jar", "--describe-module", "--file", jar.toString());
    // "No module descriptor found. Derived automatic module." followed by the name,
    // with "@<version>" when the file name carries a parsable version (pololei-1.0.0.jar)
    assertTrue(description.lines()
                          .anyMatch(line -> line.equals(module + " automatic") || line.matches(Pattern.quote(module) + "@\\S+ automatic")), description);
    // the name comes from the manifest, not from the file name
    assertTrue(description.contains("Derived automatic module") && !description.contains("pololei.HEAD"), description);
  }

  @Test
  void modulePath(@TempDir Path dir) throws IOException, InterruptedException {
    Path sources = dir.resolve("src");
    Files.createDirectories(sources.resolve("app"));
    Files.writeString(sources.resolve("module-info.java"), "module app { requires " + module + "; }");
    Files.writeString(sources.resolve("app/Main.java"), MAIN);
    Path classes = dir.resolve("classes");
    run("javac", "--module-path", jar.toString(), "-d", classes.toString(), sources.resolve("module-info.java")
                                                                                   .toString(), sources.resolve("app/Main.java")
                                                                                                       .toString());
    String output = java("--module-path", jar + java.io.File.pathSeparator + classes, "--module", "app/app.Main");
    assertEquals("FR14 2004 1010 0505 0001 3M02 606 true app " + module, output);
  }

  @Test
  void classpath(@TempDir Path dir) throws IOException, InterruptedException {
    Path sources = dir.resolve("src");
    Files.createDirectories(sources.resolve("app"));
    Files.writeString(sources.resolve("app/Main.java"), MAIN);
    Path classes = dir.resolve("classes");
    run("javac", "--class-path", jar.toString(), "-d", classes.toString(), sources.resolve("app/Main.java")
                                                                                  .toString());
    String output = java("--class-path", jar + java.io.File.pathSeparator + classes, "app.Main");
    // on the classpath everything is in the unnamed module
    assertEquals("FR14 2004 1010 0505 0001 3M02 606 true null null", output);
  }

  private static String run(String tool,
                            String... args) {
    ByteArrayOutputStream out    = new ByteArrayOutputStream();
    PrintStream           stream = new PrintStream(out, true, StandardCharsets.UTF_8);
    int code = ToolProvider.findFirst(tool)
                           .orElseThrow()
                           .run(stream, stream, args);
    String output = out.toString(StandardCharsets.UTF_8);
    assertEquals(0, code, output);
    return output;
  }

  private static String java(String... args) throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java")
                    .toString());
    command.addAll(List.of(args));
    Process process = new ProcessBuilder(command).redirectErrorStream(true)
                                                 .start();
    String output = new String(process.getInputStream()
                                      .readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, process.waitFor(), output);
    // JAVA_TOOL_OPTIONS may print a line of its own
    return output.lines()
                 .filter(line -> !line.startsWith("Picked up"))
                 .reduce("", (a, b) -> a + b);
  }
}
