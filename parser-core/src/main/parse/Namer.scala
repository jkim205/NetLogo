// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.parse

import org.nlogo.core,
  core.{FrontEndInterface, DummyExtensionManager, ExtensionManager, FrontEndProcedure, Instruction,
  Program, Token, TokenMapperInterface, TokenType},
  core.Fail._

/**
  * Classifies identifier tokens as commands or reporters.
  *
  * This is basically just a function from Iterator[Token] to Iterator[Token].
  * Most tokens pass through unchanged, but each token of type Ident is
  * replaced with a new token of type TokenType.Command or TokenType.Reporter.
  *
  * One additional check is performed: checkProcedureName makes sure the name (and input names) of
  * each procedure aren't also the name of anything else.  (That check happens here because here is
  * where the knowledge of what names are taken resides.)
  */
class Namer(
  program: Program,
  procedures: FrontEndInterface.ProceduresMap,
  extensionManager: ExtensionManager) {

  def process(tokens: Iterator[Token], procedure: FrontEndProcedure): Iterator[Token] = {
    // the handlers are mutually exclusive (only one applies), so the order the handlers
    // appear is arbitrary, except that for checkName to work, ProcedureVariableHandler
    // and CallHandler must come last - ST 5/14/13, 5/16/13
    val handlers = Stream[Token => Option[(TokenType, core.Instruction)]](
      new CommandHandler(program.dialect.tokenMapper),
      new ReporterHandler(program.dialect.tokenMapper),
      TaskVariableHandler,
      new BreedHandler(program),
      new AgentVariableReporterHandler(program),
      new ExtensionPrimitiveHandler(extensionManager),
      new ProcedureVariableHandler(procedure.args),
      new CallHandler(procedures))
    def processOne(token: Token): Option[Token] = {
      handlers.flatMap(_(token))
        .headOption
        .map{case (tpe, instr) =>
          val newToken = token.copy(tpe = tpe, value = instr)
          instr.token = newToken
          newToken
        }
    }
    def checkName(token: Token) {
      val newVal = processOne(token).map(_.value).get
      val ok = newVal.isInstanceOf[core.prim._call] ||
        newVal.isInstanceOf[core.prim._callreport] ||
        newVal.isInstanceOf[core.prim._procedurevariable]
      cAssert(ok, alreadyTaken(newVal.getClass.getSimpleName, token.text.toUpperCase), token)
    }
    for (token <- procedure.nameToken +: procedure.argTokens)
      checkName(token)
    // anything that we don't recognize, we just assume for now that it's
    // a reference to a local variable established by _let. later, LetScoper
    // will connect each _letvariable to the right _let - ST 9/3/14
    tokens.map{token => token.tpe match {
      case TokenType.Ident =>
        processOne(token).getOrElse(
          token.refine(core.prim._unknownidentifier(), tpe = TokenType.Reporter)
        )
      case _ =>
        token
    }}
  }

  private def alreadyTaken(theirs: String, ours: String) =
    "There is already a " + theirs + " called " + ours

}

object Namer {

  // provides token type information for commands, reporters, keywords, and constants
  def basicNamer(tokenMapper: TokenMapperInterface, extensionManager: ExtensionManager): Token => Token = {
    def makeToken(f: Token => Option[(TokenType, AnyRef)])(tok: Token): Token =
      if (tok.tpe == TokenType.Ident)
        f(tok) match {
          case Some((tpe, v: Instruction)) => tok.refine(v, tpe = tpe)
          case Some((tpe, v: AnyRef)) => tok.copy(tpe = tpe, value = v)
          case None                   => tok
        }
      else
        tok

    (Namer0.nameKeywordsAndConstants _)             andThen
    (makeToken(new ReporterHandler(tokenMapper)) _) andThen
    (makeToken(new CommandHandler(tokenMapper)) _)  andThen
    (makeToken(new ExtensionPrimitiveHandler(extensionManager)) _)  andThen
    ((t: Token) =>
        if (t.tpe == TokenType.Reporter && t.value.isInstanceOf[core.prim._symbol])
          t.copy(tpe = TokenType.Ident, value = t.value)
        else
          t)
  }
}
