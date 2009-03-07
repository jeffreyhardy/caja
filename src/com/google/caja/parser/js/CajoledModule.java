// Copyright (C) 2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.caja.parser.js;

import com.google.caja.lexer.FilePosition;
import com.google.caja.lexer.InputSource;
import com.google.caja.lexer.TokenConsumer;
import com.google.caja.parser.AbstractParseTreeNode;
import com.google.caja.parser.ParseTreeNode;
import com.google.caja.parser.ParseTreeNodeContainer;
import com.google.caja.parser.quasiliteral.QuasiBuilder;
import com.google.caja.render.JsPrettyPrinter;
import com.google.caja.render.SourceSpansRenderer;
import com.google.caja.reporting.MessageContext;
import com.google.caja.reporting.RenderContext;
import com.google.caja.util.Callback;
import com.google.caja.util.HandledAppendable;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The result of running the cajoler over some content.
 *
 * @author ihab.awad@gmail.com
 */
public final class CajoledModule extends AbstractParseTreeNode {
  // A stub file name by which to call the (otherwise anonymous) cajoled output.
  private static final InputSource cajoledOutputFileName =
      new InputSource(URI.create("file:///CAJOLED-OUTPUT"));

  /** @param value unused.  This ctor is provided for reflection. */
  public CajoledModule(FilePosition pos,
                       Void value,
                       List<? extends ObjectConstructor> children) {
    this(children.get(0));
    assert children.size() == 1;
  }

  /**
   * Creates a CajoledModule.
   *
   * @param pos a file position.
   * @param body an object contructor representing the module.
   */
  public CajoledModule(FilePosition pos, ObjectConstructor body) {
    super(pos, ObjectConstructor.class);
    createMutation().appendChild(body).execute();
  }

  /**
   * Creates a CajoledModule.
   *
   * @param body an object contructor representing the module.
   */
  public CajoledModule(ObjectConstructor body) {
    this(FilePosition.UNKNOWN, body);
  }

  @Override
  protected void childrenChanged() {
    super.childrenChanged();
    if (children().size() != 1) {
      throw new IllegalStateException(
          "A CajoledModule may only have one child");
    }
  }

  @Override
  public Object getValue() { return null; }

  public ObjectConstructor getModuleBody() {
    return childrenAs(ObjectConstructor.class).get(0);
  }

  public final TokenConsumer makeRenderer(
      Appendable out, Callback<IOException> exHandler) {
    return new JsPrettyPrinter(out, exHandler);
  }

  public void render(RenderContext rc) {

    Expression expr = (Expression) QuasiBuilder.substV(
        "___.loadModule(@body)",
        "body", getModuleBody());
    // Note that we deliberately add an enclosing block. See:
    // http://code.google.com/p/google-caja/issues/detail?id=1000
    Block block = new Block(
        FilePosition.UNKNOWN,
        Arrays.asList(new ExpressionStmt(FilePosition.UNKNOWN, expr)));
    block.render(rc);
  }

  public void renderWithDebugSymbols(
      Map<InputSource, CharSequence> originalSources,
      MessageContext mc,
      Appendable out,
      Callback<IOException> exHandler) {
    // Render the module function. With this, the SourceSpansRenderer captures
    // the rendered form of the function, and also builds the debug information.
    SourceSpansRenderer ssr = new SourceSpansRenderer(
        exHandler, cajoledOutputFileName);
    RenderContext rc = new RenderContext(mc, ssr);

    getModuleBody().getEntries().get("instantiate").render(rc);
    ssr.noMoreTokens();

    // Build the abbreviated original file names and their contents.
    List<String> abbreviatedOriginalFileNames = new ArrayList<String>();
    List<List<String>> originalFileContents = new ArrayList<List<String>>();

    for (InputSource is : ssr.getMessageContext().getInputSources()) {
      String sourceString = charSequenceToString(originalSources.get(is));
      List<String> lines = Arrays.asList(sourceString.split("\r\n?|\n"));
      abbreviatedOriginalFileNames.add(ssr.getMessageContext().abbreviate(is));
      originalFileContents.add(lines);
    }

    // Now render the actual text to 'out'.
    renderText(
        getModuleBody(),
        ssr.getProgramText(),
        ssr.getSourceLocationMap(),
        abbreviatedOriginalFileNames,
        originalFileContents,
        mc,
        out,
        exHandler);
  }

  // Renders the text of the module literal. This is the only place where we
  // break the rendering abstraction by printing plain text directly to the
  // output stream.
  private static void renderText(ObjectConstructor moduleBody,
                                 String instantiateFunctionText,
                                 List<String> sourceLocationMap,
                                 List<String> abbreviatedOriginalFileNames,
                                 List<List<String>> originalFileContents,
                                 MessageContext mc,
                                 Appendable out,
                                 Callback<IOException> exHandler) {
    HandledAppendable hout = new HandledAppendable(exHandler, out);

    // Open top level function call and object literal
    // Note that we deliberately add an enclosing block. See:
    // http://code.google.com/p/google-caja/issues/detail?id=1000        
    hout.append("{___.loadModule({\n");

    // Render the cajoled code
    renderNode(
        stringToStringLiteral("instantiate"),
        mc, out, exHandler);
    hout.append(":\n");
    hout.append(instantiateFunctionText);
    hout.append(",\n");

    for (String key : moduleBody.getEntries().keySet()) {
      if ("instantiate".equals(key)) { continue; }

      // Render remaining key/value pairs in the module body
      renderNode(
          stringToStringLiteral(key),
          mc, out, exHandler);
      hout.append(": ");
      renderNode(
          moduleBody.getEntries().get(key),
          mc, out, exHandler);
      hout.append(",\n");
    }

    // Render source location map
    renderNode(
        stringToStringLiteral("sourceLocationMap"),
        mc, out, exHandler);
    hout.append(": ");
    renderNode(
        stringListToContentNode(sourceLocationMap),
        mc, out, exHandler);
    hout.append(",\n");

    // Render original source
    renderNode(
        stringToStringLiteral("originalSource"),
        mc, out, exHandler);
    hout.append(": ");
    renderNode(
        buildOriginalSourceNode(
            abbreviatedOriginalFileNames,
            originalFileContents),
        mc, out, exHandler);
    hout.append("\n");

    // Close top level function call and object literal
    hout.append("});}\n");
  }
  
  private static ParseTreeNode buildOriginalSourceNode(
      List<String> abbreviatedOriginalFileNames,
      List<List<String>> originalFileContents) {
    return QuasiBuilder.substV(
        "({ @keys*: @values* })",
        "keys", stringListToStringLiterals(abbreviatedOriginalFileNames),
        "values", stringListListToMultipleContentNodes(originalFileContents));
  }

  private static ParseTreeNode stringListListToMultipleContentNodes(
      List<List<String>> contents) {
    List<ParseTreeNode> multipleContents =
        new ArrayList<ParseTreeNode>(contents.size());

    for (List<String> c : contents) {
      multipleContents.add(stringListToContentNode(c));
    }

    return new ParseTreeNodeContainer(multipleContents);
  }

  private static ParseTreeNode stringListToContentNode(List<String> lines) {
    return QuasiBuilder.substV(
        "  ({"
        + "  type: 'content',"
        + "  content: [ @stringLiterals* ]"
        + "})",
        "stringLiterals", stringListToStringLiterals(lines));
  }

  private static ParseTreeNode stringListToStringLiterals(
      List<String> strings) {
    List<ParseTreeNode> stringLiterals =
        new ArrayList<ParseTreeNode>(strings.size());

    for (String s : strings) {
      stringLiterals.add(stringToStringLiteral(s));
    }

    return new ParseTreeNodeContainer(stringLiterals);
  }

  private static ParseTreeNode stringToStringLiteral(String s) {
    return new StringLiteral(
        FilePosition.UNKNOWN,
        StringLiteral.toQuotedValue(s));
  }

  private static String charSequenceToString(CharSequence cs) {
    return cs instanceof String ?
        (String) cs :
        new StringBuilder().append(cs).toString();
  }

  private static void renderNode(ParseTreeNode node,
                                 MessageContext mc,
                                 Appendable out,
                                 Callback<IOException> exHandler) {
    TokenConsumer tc = new JsPrettyPrinter(out, exHandler);
    RenderContext rc = new RenderContext(mc, tc);
    node.render(rc);
    tc.noMoreTokens();
  }
}