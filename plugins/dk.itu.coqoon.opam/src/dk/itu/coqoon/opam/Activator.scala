package dk.itu.coqoon.opam

import org.osgi.framework.BundleContext
import org.eclipse.ui.plugin.AbstractUIPlugin

import dk.itu.coqoon.core.ICoqPathOverride
import dk.itu.coqoon.opam.ui.OPAMPreferences

class Activator extends AbstractUIPlugin {
  override def start(context : BundleContext) = {
    super.start(context)
    Activator.instance = this
  }

  override def stop(context : BundleContext) = {
    Activator.instance = null
    super.stop(context)
  }
}
object Activator {
  private var instance : Activator = _

  def getDefault() = instance
}

class Override extends ICoqPathOverride {
  import org.eclipse.core.runtime.{Path, IPath}
  override def getCoqPath() = {
    val path = OPAMPreferences.ActiveRoot.get
    path.flatMap(p => OPAM.canonicalise(new Path(p))) match {
      case Some(root) =>
        new Path(root.getPackage("coq").getConfigVar("bin"))
      case None =>
        Path.EMPTY
    }
  }
}