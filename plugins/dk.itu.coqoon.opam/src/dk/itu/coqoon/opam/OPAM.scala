package dk.itu.coqoon.opam

import org.eclipse.core.runtime.{Path, IPath}
import scala.sys.process.{Process,ProcessBuilder,ProcessLogger}

case class OPAMException(s : String) extends Exception(s)

class OPAMRoot(val path : IPath) {
  private var cache : Map[Package,Option[Package#Version]] = Map()
  
  case class Repository(val name : String, val uri : String) {
    def getRoot() = OPAMRoot.this
  }

  case class Package(val name : String) {
    def getRoot() = OPAMRoot.this

    case class Version(val version : String) {
      def getPackage() = Package.this

      def install(pin : Boolean = false,
                  logger : ProcessLogger = OPAM.drop) : Boolean = {
        val order_chars = """^[><=]""".r
        val thing = version match {
          case order_chars() => name + version
          case _ => name + "=" + version }
        val ok = OPAMRoot.this(logger, "install","-y",thing)
        var pin_ok = true
        if (ok && pin) 
            OPAMRoot.this.getPackage(name).getInstalledVersion.foreach(v =>
              pin_ok = OPAMRoot.this(logger, "pin","add",name,v.version))
        fillCache
        ok && pin_ok
      }
      
      def uninstall() : Boolean = { 
        val ok = OPAMRoot.this("remove","-y",name)
        fillCache
        ok
      }
      
    } /* Version */
    
    def installAnyVersion(logger : ProcessLogger = OPAM.drop) : Boolean = {
      val ok = OPAMRoot.this(logger, "install","-y",this.name)
      fillCache
      ok
    }
      
    
    def getDescription() : String =
      read("show","-f","description",name).mkString("\n")


    def getAvailableVersions() : Seq[Version] = {
      val version = """([^, ]++)""".r
      val versions_str =
        read("show","-f","available-version,available-versions",name).head.split(':')(1)
      (version findAllIn versions_str).map(new Version(_)).toList
    }

    def getInstalledVersion() : Option[Package#Version] =
      try cache(this)
      catch { case e : NoSuchElementException => None }

    /*{
      val v = read("config","var",name + ":version").head 
      if (v == "#undefined") None else Some(new Version(v))
    }*/

    def getLatestVersion() : Option[Version] =
      getAvailableVersions().lastOption

    def getVersion(version : String) : Version = new Version(version)

  } /* Package */

  
  def getRepositories() : Seq[Repository] = {
    val repo = """.*?(\S++)\s++(\S++)$""".r
    read("repo","list").map(_ match {
        case repo(name,uri) => new Repository(name,uri)
        case s => throw new OPAMException("error parsing " + s)
      })
  }
  
  def addRepositories(logger : ProcessLogger, repos : Repository*) : Unit =
    repos.foreach(r => this(logger, "repo","add",r.name,r.uri))
  def addRepositories(repos : Repository*) : Unit =
    addRepositories(OPAM.drop, repos:_*)

  def getPackages(filter : Package => Boolean = _ => true) : Seq[Package] =
    cache.keys.toList.filter(filter)
  /*{
    read("list","-a","-s",filter).map(s => new Package(s))
  }*/
  
  def getPackage(name : String) : Package = new Package(name)
  
  def fillCache() : Unit = {   
    val name_ver = """^(\S++)\s++(\S++).*""".r
    read("list","-a").foreach(line =>
      line match {
        case name_ver(name,version) => {
          val p = new Package(name)
          val v = if (version == "--" || name(0) == '#') None
                  else Some(new p.Version(version))
          cache += (p -> v) }
        case _ =>
      })
  }
  
  private [opam] def opam(args : String*) : ProcessBuilder = {
    Process(command="opam" +: args, cwd=None, "OPAMROOT" -> path.toString)
  }
  
  private[opam] def read(cmd : String*) : Seq[String] = {
    try opam(cmd:_*).lineStream.toList
    catch { case e : RuntimeException => throw new OPAMException(e.getMessage) }
  }

  private[opam] def apply(cmd : String*) : Boolean = {
    opam(cmd:_*).run.exitValue() == 0
  }
  private[opam] def apply(logger : ProcessLogger, cmd : String*) : Boolean = {
    logger.out(cmd.toString())
    opam(cmd :+ "-v" :_*).run(logger).exitValue() == 0
  }
  
} /* OPAMRoot */

object OPAM {
  import dk.itu.coqoon.opam.ui.OPAMPreferences
  def getRoots() : Seq[OPAMRoot] =
    OPAMPreferences.Roots.get.map(p => {
      val root = new OPAMRoot(new Path(p))
      root.fillCache
      root })
  def drop = ProcessLogger(s => ())
  def initRoot(path : IPath,
               ocaml : String = "system",
               logger : ProcessLogger = drop) = {
    val root = new OPAMRoot(path)
    val is_root = path.addTrailingSeparator.append("config").toFile.exists()
    val is_empty_dir = path.toFile.isDirectory() && path.toFile.list().isEmpty
    if (!is_root)
      if (is_empty_dir || !path.toFile.exists()) root(logger,"init","--comp="+ocaml,"-j","2","-n")
      else throw new OPAMException("path " + path + " is a non empty directory")
    OPAMPreferences.Roots.set(OPAMPreferences.Roots.get() :+ path.toString)
    root
  }
  
  /* 
  def main(args : Array[String]) = {
    val tmp = "/tmp/test"
    //assert(Process("rm",Seq("-rf",tmp)).run().exitValue() == 0)
    val r = initRoot(new Path(tmp),"system")
    //r.addRepositories(new r.Repository("coq","http://coq.inria.fr/opam/released"))
    assert(r.getRepositories().length == 2, "#repos")
    println(r.getPackages())
    println(r.getPackage("camlp5").getAvailableVersions())
    println(r.getPackage("camlp5").getInstalledVersion())
    println(r.getPackage("camlp5").getLatestVersion.foreach(_.install(pin = false)))
    println(r.getPackage("camlp5").getInstalledVersion())
    println(r.getPackage("camlp5").getInstalledVersion().foreach(_.uninstall))
  }
  */
}