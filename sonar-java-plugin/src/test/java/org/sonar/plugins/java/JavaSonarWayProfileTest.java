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
package org.sonar.plugins.java;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonar.api.profiles.AnnotationProfileParser;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RuleFinder;
import org.sonar.api.utils.ValidationMessages;
import org.sonar.java.checks.CheckList;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JavaSonarWayProfileTest {

  @Test
  public void should_create_sonar_way_profile() {
    ValidationMessages validation = ValidationMessages.create();

    JavaSonarWayProfile definition = new JavaSonarWayProfile(new AnnotationProfileParser(ruleFinder()));
    RulesProfile profile = definition.createProfile(validation);

    assertThat(profile.getLanguage()).isEqualTo(Java.KEY);
    assertThat(profile.getActiveRulesByRepository(CheckList.REPOSITORY_KEY))
      .hasSize(127);
    assertThat(profile.getName()).isEqualTo("Sonar way");
    assertThat(validation.hasErrors()).isFalse();
  }

  static RuleFinder ruleFinder() {
    return when(mock(RuleFinder.class).findByKey(anyString(), anyString())).thenAnswer(new Answer<Rule>() {
      @Override
      public Rule answer(InvocationOnMock invocation) {
        Object[] arguments = invocation.getArguments();
        return Rule.create((String) arguments[0], (String) arguments[1], (String) arguments[1]);
      }
    }).getMock();
  }

}
