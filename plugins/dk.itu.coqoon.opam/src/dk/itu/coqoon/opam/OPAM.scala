package dk.itu.coqoon.opam

import org.eclipse.core.runtime.{Path, IPath}

class OPAMRoot(val path : IPath) {
  class Repository(val name : String, val uri : String)

  class Package(val name : String) {
    class Version(val version : String) {
      def install() : Boolean = false
      def uninstall() : Boolean = false
    }
    def getDescription() : String = ""

    def getAvailableVersions() : Seq[Version] = Seq()
    def getInstalledVersion() : Option[Version] = None
    def getLatestVersion() : Option[Version] =
      getAvailableVersions().lastOption
    def getVersion(version : String) : Version = new Version(version)
  }

  def getRepositories() : Seq[Repository] = Seq()
  def setRepositories(repos : Seq[Repository]) = ()

  def getPackages() : Seq[Package] = Seq()
  def getPackage(name : String) : Package = new Package(name)
}

object OPAM {
  var roots : Seq[OPAMRoot] = Seq()
  def getRoots() : Seq[OPAMRoot] = roots
  def initRoot(path : IPath) = {
    val root = new OPAMRoot(path)
    path.toFile.mkdir()
    roots :+= root
    root
  }
}