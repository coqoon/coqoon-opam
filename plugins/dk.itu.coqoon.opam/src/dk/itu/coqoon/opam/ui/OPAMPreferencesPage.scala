package dk.itu.coqoon.opam.ui

import dk.itu.coqoon.ui.utilities.{UIXML, Event, Listener}
import dk.itu.coqoon.core.utilities.CacheSlot
import dk.itu.coqoon.opam.{OPAM, OPAMException, Activator}
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.swt.{events, widgets}
import org.eclipse.core.runtime.{Path, IPath, Status, IStatus, IProgressMonitor}
import org.eclipse.jface.preference.PreferencePage
import org.eclipse.jface.operation.IRunnableWithProgress
import org.eclipse.jface.dialogs.{ProgressMonitorDialog, Dialog}

class InitJob(val path : Path, val ocaml : String, val coq : String) extends IRunnableWithProgress {
  
  val f = new java.io.FileWriter(new java.io.File("/tmp/log.txt"))
    
  def log(m : IProgressMonitor, prefix : String) : scala.sys.process.ProcessLogger =
    scala.sys.process.ProcessLogger((s) => { f.write(s); f.write("\n"); f.flush(); m.subTask(prefix + ":\n " + s) })
  
  var error : Option[String] = None  
    
  override def run(monitor : IProgressMonitor) = try {
    monitor.beginTask("Initialise OPAM root", 6)
    
    val logger = log(monitor, _ : String)

    val r = OPAM.initRoot(path, ocaml, logger = logger("Initializing"))
    monitor.worked(1)

    r.addRepositories(logger("Adding repository: Coq released"),
        new r.Repository("coq","http://coq.inria.fr/opam/released"))
    monitor.worked(1)

    val dev_version = """dev$""".r  
    coq match {
      case dev_version() => 
        r.addRepositories(logger("Adding repository: Coq core-dev"),
          new r.Repository("core-dev","http://coq.inria.fr/opam/core-dev"))
      case _ => // no need for extra repos
    }
    monitor.worked(1)
        
    r.getPackage("coq").getVersion(coq).install(true, logger("Building Coq"))
    monitor.worked(1)
    
    r.addRepositories(logger("Adding repository: Coqoon"),
        new r.Repository("coqoon","http://bitbucket.org/coqpide/opam.git"))
    monitor.worked(1)

    val pidetop = r.getPackage("pidetop").getLatestVersion()
    pidetop match {
      case None => throw new OPAMException("Coqoon needs pidetop")
      case Some(v) => v.install(false,logger("Building pidetop"))
    }
    monitor.worked(1)

  } catch {
    case e : OPAMException => error = Some(e.getMessage)
  } finally {
    monitor.done()
  }
}

class OPAMRootCreation(s : org.eclipse.swt.widgets.Shell)
  extends Dialog(s) {
  
    var path = ""
    var coq = ""
    var ocaml = ""
    
    //this.getShell.setText("OPAM root parameters")
    
    override def createDialogArea(c : widgets.Composite) = {
      val names = UIXML(
        <composite name="root">
          <grid-layout columns="3" />

				  <label>Path:<tool-tip>Select a directory</tool-tip></label>
				  <text name="path"/>
				  <button name="sel-path">Select...</button>

          <label>Coq:<tool-tip>Coq version to install</tool-tip></label>
          <combo name="coq">
            <grid-data h-grab="true" h-span="2" />
          </combo>
          <label>OCaml:<tool-tip>OCaml version to install</tool-tip></label>
          <combo name="ocaml">
            <grid-data h-grab="true" h-span="2" />
          </combo>
        </composite>,c)

      val wcoq = names.get[widgets.Combo]("coq")
      wcoq.foreach(wcoq => {
        Listener.Modify(wcoq, Listener {
          case Event.Modify(_) => coq = wcoq.getText.trim
        })
        wcoq.add("8.5.2")
        wcoq.add("8.6.dev")
        wcoq.select(0)
      })
      
      val wocaml = names.get[widgets.Combo]("ocaml")
      wocaml.foreach(wocaml => {
        Listener.Modify(wocaml, Listener {
          case Event.Modify(_) => ocaml = wocaml.getText.trim
        })
        wocaml.add("4.01.0")
        wocaml.add("4.02.3")
        wocaml.add("system")
        wocaml.select(0)
      })
      
      val wpath = names.get[widgets.Text]("path")
      wpath.foreach(wpath => { 
        Listener.Modify(wpath, Listener {
          case Event.Modify(_) => path = wpath.getText.trim
        })
        wpath.setText(System.getenv("HOME") + "/opam-roots/coq-" + 
            wocaml.get.getText + "-" + wcoq.get.getText)
      })
     
      Listener.Selection(names.get[widgets.Button]("sel-path").get, Listener {
        case Event.Selection(ev) =>
          val d = new widgets.DirectoryDialog(s)
          Option(d.open()).map(_.trim).filter(_.length > 0) match {
            case Some(p) => wpath.get.setText(p)
            case _ => }
      })
      
      names.get[widgets.Composite]("root").get
   }
    
}

class OPAMPreferencesPage
    extends PreferencePage with IWorkbenchPreferencePage {
  private var roots = CacheSlot(OPAMPreferences.Roots.get.toBuffer)
  private var activeRoot = CacheSlot(OPAMPreferences.ActiveRoot.get)
  override def performOk() = {
    if (roots.get != OPAMPreferences.Roots.get.toBuffer)
      OPAMPreferences.Roots.set(roots.get)
    if (activeRoot.get != OPAMPreferences.ActiveRoot.get)
      activeRoot.get.foreach(OPAMPreferences.ActiveRoot.set)
    true
  }

  override def init(w : IWorkbench) = ()
  override def createContents(c : widgets.Composite) = {
    val names = UIXML(
        <composite name="root">
          <grid-layout columns="4" />
          <label>
            Root:
          </label>
          <combo name="roots">
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
    names.get[widgets.Combo]("combo").foreach(combo => {
      val roots = OPAMPreferences.Roots.get
      if (roots.length == 0) {
        combo.setEnabled(false)
        combo.setText("(none)")
      } else {
        combo.setEnabled(true)
        roots.foreach(combo.add)
      }
      Listener.Selection(combo, Listener {
        case Event.Selection(ev) =>
          activeRoot.set(Some(Some(
              combo.getItem(combo.getSelectionIndex()))))
          /* XXX: also update the viewers */
      })
    })
    val button = names.get[widgets.Button]("add").get
    val shell = button.getShell
    Listener.Selection(button, Listener {
      case Event.Selection(ev) =>
        val d = new OPAMRootCreation(button.getShell)
        if (d.open() == org.eclipse.jface.window.Window.OK) {
           val create_root = d.coq != "" && d.ocaml != "" && d.path != ""
           if (create_root) try {
               val op = new InitJob(new Path(d.path),d.ocaml,d.coq)
               val dialog = new org.eclipse.jface.dialogs.ProgressMonitorDialog(this.getShell)
               dialog.run(true, true, op)
               op.error match {
                 case Some(s) => this.setErrorMessage(s)
                 case None => /* XXX */}
           } catch {
               case e : java.lang.reflect.InvocationTargetException =>
                 this.setErrorMessage(e.getMessage)
               case e : InterruptedException =>
                 this.setErrorMessage(e.getMessage)
           }
        }
    })
    names.get[widgets.Composite]("root").get
  }
}

object OPAMPreferences {
  object Roots {
    final val ID = "roots"
    def get() =
      Activator.getDefault.getPreferenceStore.getString(ID).split(";")
    def set(roots : Seq[String]) =
      if (roots.forall(!_.contains(";"))) {
        Activator.getDefault.getPreferenceStore().setValue(
            ID, roots.mkString(";"))
      }
  }
  object ActiveRoot {
    final val ID = "activeRoot"
    def get() = {
      val raw = Activator.getDefault.getPreferenceStore.getString(ID)
      if (raw.length > 0 && Roots.get.contains(raw)) {
        Some(raw)
      } else None
    }
    def set(root : String) =
      if (Roots.get.contains(root))
        Activator.getDefault.getPreferenceStore.setValue(ID, root)
  }
}