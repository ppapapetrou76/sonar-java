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
package org.sonar.java;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.java.bytecode.visitor.ResourceMapping;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.JavaResourceLocator;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public class DefaultJavaResourceLocator implements JavaResourceLocator, JavaFileScanner {

  private static final Logger LOG = LoggerFactory.getLogger(JavaResourceLocator.class);

  private final Project project;
  private final JavaClasspath javaClasspath;
  @VisibleForTesting
  Map<String, Resource> resourcesByClass;
  private final Map<String, String> sourceFileByClass;
  private final Map<String, Integer> methodStartLines;
  private final ResourceMapping resourceMapping;

  public DefaultJavaResourceLocator(Project project, JavaClasspath javaClasspath) {
    this.project = project;
    this.javaClasspath = javaClasspath;
    resourcesByClass = Maps.newHashMap();
    sourceFileByClass = Maps.newHashMap();
    methodStartLines = Maps.newHashMap();
    resourceMapping = new ResourceMapping();
  }

  @Override
  public Resource findResourceByClassName(String className) {
    String name = className.replace('.', '/');
    Resource resource = resourcesByClass.get(name);
    if (resource == null) {
      LOG.debug("Class not found in resource cache : {}", className);
    }
    return resource;
  }

  @Override
  public String findSourceFileKeyByClassName(String className) {
    String name = className.replace('.', '/');
    return sourceFileByClass.get(name);
  }

  @Override
  public Collection<String> classKeys() {
    return ImmutableSortedSet.<String>naturalOrder().addAll(resourcesByClass.keySet()).build();
  }

  @Override
  public Collection<File> classFilesToAnalyze() {
    ImmutableList.Builder<File> result = ImmutableList.builder();
    for (String key : classKeys()) {
      String filePath = key + ".class";
      for (File binaryDir : javaClasspath.getBinaryDirs()) {
        File classFile = new File(binaryDir, filePath);
        if (classFile.isFile()) {
          result.add(classFile);
          break;
        }
      }
    }
    return result.build();
  }

  @Override
  public Collection<File> classpath() {
    return javaClasspath.getElements();
  }

  @Override
  public Integer getMethodStartLine(String fullyQualifiedMethodName) {
    return methodStartLines.get(fullyQualifiedMethodName);
  }

  @Override
  public ResourceMapping getResourceMapping() {
    return resourceMapping;
  }

  @Override
  public void scanFile(JavaFileScannerContext context) {
    JavaFilesCache javaFilesCache = new JavaFilesCache();
    javaFilesCache.scanFile(context);
    org.sonar.api.resources.File currentResource = org.sonar.api.resources.File.fromIOFile(context.getFile(), project);
    if (currentResource == null) {
      throw new IllegalStateException("resource not found : " + context.getFileKey());
    }
    resourceMapping.addResource(currentResource, context.getFileKey());
    for (Map.Entry<String, File> classIOFileEntry : javaFilesCache.getResourcesCache().entrySet()) {
      resourcesByClass.put(classIOFileEntry.getKey(), currentResource);
      if (context.getFileKey() != null) {
        sourceFileByClass.put(classIOFileEntry.getKey(), context.getFileKey());
      }
    }
    context.addNoSonarLines(javaFilesCache.ignoredLines());
    methodStartLines.putAll(javaFilesCache.getMethodStartLines());
  }

}
