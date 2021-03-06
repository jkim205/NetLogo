// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.api

import org.nlogo.core.CompilerException

trait LogoThunkFactory {
  @throws(classOf[CompilerException])
  def makeReporterThunk(code: String, jobOwnerName: String): ReporterLogoThunk
  @throws(classOf[CompilerException])
  def makeCommandThunk(code: String, jobOwnerName: String): CommandLogoThunk
  @throws(classOf[CompilerException])
  def makeCommandThunk(code: String, jobOwnerName: String, rng: MersenneTwisterFast): CommandLogoThunk
}
