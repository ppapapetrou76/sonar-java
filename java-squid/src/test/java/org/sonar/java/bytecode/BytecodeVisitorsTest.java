/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.bytecode;

import org.junit.BeforeClass;
import org.junit.Test;
import org.sonar.api.design.Dependency;
import org.sonar.api.resources.Directory;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.ProjectFileSystem;
import org.sonar.api.resources.Resource;
import org.sonar.graph.DirectedGraph;
import org.sonar.java.DefaultJavaResourceLocator;
import org.sonar.java.JavaConfiguration;
import org.sonar.java.JavaSquid;
import org.sonar.java.bytecode.visitor.ResourceMapping;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BytecodeVisitorsTest {

  private static DirectedGraph<Resource, Dependency> graph;

  static Resource todo;
  static Resource fixme;
  static Resource file;
  static Resource tag;
  static Resource tagFile;
  static Resource line;
  static Resource sourceFile;
  static Resource language;
  static Resource tagName;
  static Resource tagException;
  static Resource pacTag;
  static Resource pacImpl;
  static ResourceMapping resourceMapping;

  @BeforeClass
  public static void setup() {
    JavaConfiguration conf = new JavaConfiguration(Charset.forName("UTF-8"));
    Project project = mock(Project.class);
    ProjectFileSystem pfs = mock(ProjectFileSystem.class);
    File baseDir = new File("src/test/files/bytecode/src");
    when(project.getFileSystem()).thenReturn(pfs);
    when(pfs.getBasedir()).thenReturn(baseDir);
    DefaultJavaResourceLocator javaResourceLocator = new DefaultJavaResourceLocator(project, null);
    JavaSquid squid = new JavaSquid(conf, javaResourceLocator);
    squid.scanDirectories(
        Collections.singleton(new File("src/test/files/bytecode/src")),
        Collections.singleton(new File("src/test/files/bytecode/bin")));
    graph = squid.getGraph();
    resourceMapping = javaResourceLocator.getResourceMapping();
    tag = findResource("tags/Tag.java");
    tagFile = findResource("tags/Tag.java");
    file = findResource("tags/File.java");
    line = findResource("tags/Line.java");
    tagName = findResource("tags/TagName.java");
    tagException = findResource("tags/TagException.java");
    language = findResource("tags/Language.java");
    sourceFile = findResource("tags/SourceFile.java");
    todo = findResource("tags/impl/Todo.java");
    fixme = findResource("tags/impl/FixMe.java");
    pacTag = findResource("tags");
    pacImpl = findResource("tags/impl");
  }

  @Test
  public void testExtendsRelationShips() {
    assertThat(graph.getEdge(sourceFile, file).getUsage()).isEqualTo("USES");
  }

  @Test
  public void testClassDefinitionWithGenerics() {
    assertThat(graph.getEdge(todo, language).getUsage()).isEqualTo("USES");
  }

  @Test
  public void testImplementsRelationShips() {
    assertThat(graph.getEdge(todo, tag).getUsage()).isEqualTo("USES");
  }

  @Test
  public void testLdcRelationShips() {
    assertThat(graph.getEdge(tagName, tagException).getUsage()).isEqualTo("USES");
  }

  @Test
  public void testFieldRelationShip() {
    assertThat(graph.getEdge(todo, file).getUsage()).isEqualTo("USES");
  }

  @Test
  public void testFieldRelationShipWithGenerics() {
    assertThat(graph.getEdge(todo, line).getUsage()).isEqualTo("USES");
  }

  @Test
  public void testMethodReturnType() {
    assertThat(graph.getEdge(todo, tagName).getUsage()).isEqualTo("USES");
  }

  @Test
  public void testMethodArgs() {
    assertThat(graph.getEdge(todo, sourceFile).getUsage()).isEqualTo("USES");
  }

  @Test
  public void testMethodException() {
    assertThat(graph.getEdge(todo, tagException).getUsage()).isEqualTo("USES");
  }

  @Test
  public void testAccessFieldOfAnObject() {
    assertThat(graph.getEdge(fixme, sourceFile).getUsage()).isEqualTo("USES");
  }

  @Test
  public void testTypeInsn() {
    assertThat(graph.getEdge(fixme, file).getUsage()).isEqualTo("USES");
  }

  @Test
  public void testAccessMethodOfAnObject() {
    assertThat(graph.getEdge(fixme, tagException).getUsage()).isEqualTo("USES");
  }

  @Test
  public void testTryCatchBlock() {
    assertThat(graph.getEdge(sourceFile, tagException).getUsage()).isEqualTo("USES");
  }

  @Test
  public void testPackageDependencies() {
    assertThat(graph.getEdge(pacImpl, pacTag).getUsage()).isEqualTo("USES");
    assertThat(graph.getEdge(pacImpl, pacTag).getWeight()).isEqualTo(13);
  }

  @Test
  public void noDependencyFromOneSquidUnitToItself() {
    assertThat(graph.getEdge(pacTag, pacTag)).isNull();
    assertThat(graph.getEdge(fixme, fixme)).isNull();
  }

  @Test
  public void testFileDependencies() {
    assertThat(graph.getEdge(sourceFile, tagException).getUsage()).isEqualTo("USES");
  }


  private static Resource findResource(String resource) {
    Set<Resource> directories = resourceMapping.directories();
    for (Resource directory : directories) {
      if (directory.getKey().endsWith(resource)) {
        return directory;
      }
      Collection<Resource> files = resourceMapping.files((Directory) directory);
      for (Resource file : files) {
        if (file.getKey().endsWith(resource)) {
          return file;
        }
      }
    }
    return null;
  }
}
