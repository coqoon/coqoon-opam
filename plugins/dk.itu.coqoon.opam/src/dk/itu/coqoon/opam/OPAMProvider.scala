package dk.itu.coqoon.opam

import dk.itu.coqoon.core.model._

class OPAMProvider extends LoadPathImplementationProvider {
  lazy val root = OPAM.getRoots.head

  class OPAMImplementation(
      p : OPAMRoot#Package) extends LoadPathImplementation {
    override def getName() = p.name
    override def getAuthor() = ""
    override def getDescription = p.getDescription
    override def getIdentifier = "opam:" + p.name
    override def getLoadPath =
      p.getInstalledVersion match {
        case Some(v) =>
          /* If a Coq package is installed, then it's in the user-contrib
           * folder; that means it'll be found anyway and we can just return an
           * empty fragment to indicate success */
          Right(Seq())
        case None =>
          Left(p.getAvailableVersions match {
            case l if l.length > 0 =>
              LoadPathImplementation.Retrievable
            case l =>
              LoadPathImplementation.NotRetrievable
          })
      }
    override def getProvider = OPAMProvider.this
  }

  def getName() = "OPAM"
  def getImplementation(id : String) =
    if (id.startsWith("opam:")) {
      Some(new OPAMImplementation(root.getPackage(id.substring(5))))
    } else None
  def getImplementations() =
    root.getPackages().filter(_.name.startsWith("coq-")).map(
        new OPAMImplementation(_))
}