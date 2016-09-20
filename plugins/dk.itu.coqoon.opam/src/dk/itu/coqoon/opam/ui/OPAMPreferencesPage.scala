package dk.itu.coqoon.opam.ui

import dk.itu.coqoon.ui.utilities.{UIXML, Event, Listener}
import dk.itu.coqoon.opam.OPAM
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.swt.{events, widgets}
import org.eclipse.core.runtime.{Path, IPath, Status, IStatus, IProgressMonitor}
import org.eclipse.jface.preference.PreferencePage
import org.eclipse.jface.operation.IRunnableWithProgress

class InitJob(val path : Path) extends IRunnableWithProgress {
  override def run(monitor : IProgressMonitor) = {
    monitor.beginTask("Initialise OPAM root", 3)

    monitor.subTask("Initialising")
    val r = OPAM.initRoot(path)
    monitor.worked(1)

    monitor.subTask("Adding Coq repository")
    r.addRepositories(
        new r.Repository("coq","http://coq.inria.fr/opam/released"))
    monitor.worked(1)

    monitor.subTask("Building Coq")
    r.getPackage("coq").getLatestVersion.foreach(_.install)
    monitor.worked(1)

    monitor.done()
  }
}

class OPAMPreferencesPage
    extends PreferencePage with IWorkbenchPreferencePage {
  override def init(w : IWorkbench) = ()
  override def createContents(c : widgets.Composite) = {
    val names = UIXML(
        <composite name="root">
          <grid-layout columns="4" />
          <label>
            Root:
          </label>
          <combo>
            <grid-data h-grab="true" />
          </combo>
          <button name="add">
            Add...
          </button>
          <button enabled="false">
            Remove...
          </button>
          <label separator="horizontal">
            <grid-data h-span="4" />
          </label>
          <label>
            Location:
          </label>
          <label>
            /home/user/dummy/label/what
            <grid-data h-span="3" />
          </label>
          <tab-folder>
            <grid-data h-span="4" grab="true" />
            <tab label="Repositories">
              <tree-viewer name="tf0" />
            </tab>
            <tab label="Packages">
              <tree-viewer name="tf1" />
            </tab>
          </tab-folder>
        </composite>, c)
    val button = names.get[widgets.Button]("add").get
    Listener.Selection(button, Listener {
      case Event.Selection(ev) =>
        val d = new widgets.DirectoryDialog(button.getShell)
        Option(d.open()).map(_.trim).filter(_.length > 0) match {
          case Some(path) =>
             try {
               val op = new InitJob(new Path(path))
               val dialog =
                 new org.eclipse.jface.dialogs.ProgressMonitorDialog(
                     button.getShell)
               dialog.run(true, true, op)
             } catch {
               case e : java.lang.reflect.InvocationTargetException =>
               case e : InterruptedException =>
             }
          case _ =>
        }
    })
    names.get[widgets.Composite]("root").get
  }
}