package dk.itu.coqoon.opam

import org.eclipse.core.runtime.{Path, IPath}
import scala.sys.process.{Process,ProcessBuilder,ProcessLogger}

class OPAMRoot(val path : IPath) {
  class Repository(val name : String, val uri : String)

  case class Package(val name : String) {
    
    private val order_chars = """^[><=]""".r
    
    case class Version(val version : String) {
      def install() : Boolean = {
        val thing = version match {
          case order_chars() => name + version
          case _ => name + "=" + version }
        OPAMRoot.this("install","-y",thing) 
      }
      def uninstall() : Boolean = {
        OPAMRoot.this("remove","-y",name) 
      }
    }
    def getDescription() : String = {
      read("show","-f","description",name).mkString("\n")
    }

    private val version = """([^, ]++)""".r
    
    def getAvailableVersions() : Seq[Version] = {
      val versions_str = read("show","-f","available-versions",name).head
      (version findAllIn versions_str).map(new Version(_)).toList
    }

    def getInstalledVersion() : Option[Version] = {
      val v = read("config","var",name + ":version").head 
      if (v == "#undefined") None else Some(new Version(v))
    }

    def getLatestVersion() : Option[Version] =
      getAvailableVersions().lastOption

    def getVersion(version : String) : Version = new Version(version)
  }

  private val repo = """.*(\S++)\s++(\S++)$""".r
  
  def getRepositories() : Seq[Repository] = {
    read("repo","list").map(_ match {
        case repo(name,uri) => new Repository(name,uri)
        case s => throw new Exception("error parsing " + s)
      })
  }
  
  def addRepositories(repos : Repository*) = {
    repos.foreach((r) => this("repo","add",r.name,r.uri))
  }

  def getPackages() : Seq[Package] = {
    read("list","-a","-s").map((s) => new Package(s))
  }
  def getPackage(name : String) : Package = new Package(name)
  
  private [opam] def opam(args : String*) : ProcessBuilder = {
    Process(command="opam" +: args, cwd=None, "OPAMROOT" -> path.toString)
  }
  
  private[opam] def read(cmd : String*) : Seq[String] = {
    opam(cmd:_*).lineStream.toList
  }

  private[opam] def apply(cmd : String*) : Boolean = {
    opam(cmd:_*).run.exitValue() == 0
  }
  private[opam] def apply(logger : ProcessLogger, cmd : String*) : Boolean = {
    opam(cmd :+ "-v" :_*).run(logger).exitValue() == 0
  }
}

object OPAM {

  var roots : Seq[OPAMRoot] = Seq()
  def getRoots() : Seq[OPAMRoot] = roots
  def drop = ProcessLogger((s) => ())
  def initRoot(path : IPath, logger : ProcessLogger = drop) = {
    val root = new OPAMRoot(path)
    val is_root = path.addTrailingSeparator.append("config").toFile.exists()
    val is_empty_dir = path.toFile.isDirectory() && path.toFile.list().isEmpty
    if (!is_root)
      if (is_empty_dir) root(logger, "init","-j","2","-n")
      else throw new Exception("path " + path + " is a non empty directory")
    roots :+= root
    root
  }
  /*
  def main(args : Array[String]) = {
    val tmp = "/tmp/test"
    //assert(Process("rm",Seq("-rf",tmp)).run().exitValue() == 0)
    val r = initRoot(new Path(tmp))
    //r.addRepositories(new r.Repository("coq","http://coq.inria.fr/opam/released"))
    assert(r.getRepositories().length == 2, "#repos")
    println(r.getPackages())
    println(r.getPackage("camlp5").getAvailableVersions())
    println(r.getPackage("camlp5").getInstalledVersion())
    println(r.getPackage("camlp5").getLatestVersion.foreach(_.install))
    println(r.getPackage("camlp5").getInstalledVersion())
    println(r.getPackage("camlp5").getInstalledVersion().foreach(_.uninstall))
  }
  */
}