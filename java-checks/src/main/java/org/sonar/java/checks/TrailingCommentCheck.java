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
package org.sonar.java.checks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.StringUtils;
import org.sonar.check.BelongsToProfile;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.java.model.InternalSyntaxToken;
import org.sonar.java.model.InternalSyntaxTrivia;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.tree.SyntaxToken;
import org.sonar.plugins.java.api.tree.SyntaxTrivia;
import org.sonar.plugins.java.api.tree.Tree;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Rule(
  key = "TrailingCommentCheck",
  priority = Priority.MINOR,
  tags={"convention"})
@BelongsToProfile(title = "Sonar way", priority = Priority.MINOR)
public class TrailingCommentCheck extends SubscriptionBaseVisitor {

  private static final String DEFAULT_LEGAL_COMMENT_PATTERN = "^\\s*+[^\\s]++$";
  private static final Set<String> EXCLUDED_PATTERNS = ImmutableSet.of("NOSONAR", "NOPMD", "CHECKSTYLE:");

  @RuleProperty(
    key = "legalCommentPattern",
    defaultValue = DEFAULT_LEGAL_COMMENT_PATTERN)
  public String legalCommentPattern = DEFAULT_LEGAL_COMMENT_PATTERN;

  private Pattern pattern;
  private int previousTokenLine;

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.TRIVIA);
  }

  @Override
  public void scanFile(JavaFileScannerContext context) {
    previousTokenLine = -1;
    pattern = Pattern.compile(legalCommentPattern);
    super.scanFile(context);
  }

  @Override
  public void visitToken(SyntaxToken syntaxToken) {
    int tokenLine = ((InternalSyntaxToken) syntaxToken).getLine();
    if (tokenLine != previousTokenLine) {
      for (SyntaxTrivia trivia : syntaxToken.trivias()) {
        if (((InternalSyntaxTrivia)trivia).getLine() == previousTokenLine) {
          String comment = trivia.comment();

          comment = comment.startsWith("//") ? comment.substring(2) : comment.substring(2, comment.length() - 2);
          comment = comment.trim();

          if (!pattern.matcher(comment).matches() && !containsExcludedPattern(comment)) {
            addIssue(previousTokenLine, "Move this trailing comment on the previous empty line.");
          }
        }
      }
    }

    previousTokenLine = tokenLine;
  }

  private boolean containsExcludedPattern(String comment) {
    for (String excludePattern : EXCLUDED_PATTERNS) {
      if (StringUtils.containsIgnoreCase(comment, excludePattern)) {
        return true;
      }
    }
    return false;
  }
}
