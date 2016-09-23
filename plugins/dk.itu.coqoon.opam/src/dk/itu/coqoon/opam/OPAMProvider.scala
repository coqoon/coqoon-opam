package dk.itu.coqoon.opam

import dk.itu.coqoon.core.model._
import dk.itu.coqoon.opam.ui.OPAMPreferences
import org.eclipse.core.runtime.{Path, IPath}

class OPAMProvider extends LoadPathImplementationProvider {
  class OPAMImplementation(
      p : OPAMRoot#Package) extends LoadPathImplementation {
    override def getName() = p.name
    override def getAuthor() = ""
    override def getDescription = p.getDescription
    override def getIdentifier = p.name
    override def getLoadPath =
      p.getInstalledVersion match {
        case Some(v) =>
          /* If a Coq package is installed, then it's in the user-contrib
           * folder; that means it'll be found anyway and we can just return an
           * empty fragment to indicate success */
          Right(Seq())
        case None =>
          /* If we get here, then the name of this package is known to OPAM and
           * we know it has some available versions; indicate that it could be
           * installed */
          Left(LoadPathImplementation.Retrievable)
      }
    override def getProvider = OPAMProvider.this
  }

  private def getRoot() =
    OPAMPreferences.ActiveRoot.get.map(s => OPAM.canonicalise(new Path(s)))

  def getName() = "OPAM"
  def getImplementation(id : String) =
    getRoot match {
      case Some(r) =>
        val p = r.getPackage(id)
        if (p.getLatestVersion != None) {
          Some(new OPAMImplementation(p))
        } else None
      case _ => None
    }
  def getImplementations() =
    getRoot.toSeq.flatMap(_.getPackages(
        _.name.startsWith("coq-"))).map(new OPAMImplementation(_))
}