package dk.itu.coqoon.opam.ui

import dk.itu.coqoon.ui.utilities.{UIXML, Event, Listener}
import dk.itu.coqoon.core.utilities.CacheSlot
import dk.itu.coqoon.opam.{OPAM, OPAMRoot, OPAMException, Activator}
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.swt.{events, widgets}
import org.eclipse.core.runtime.{Path, IPath, Status, IStatus, IProgressMonitor}
import org.eclipse.jface.viewers
import org.eclipse.jface.preference.PreferencePage
import org.eclipse.jface.operation.IRunnableWithProgress
import org.eclipse.jface.dialogs.{ProgressMonitorDialog, Dialog}

object LOG {
  def log(m : IProgressMonitor, prefix : String) : scala.sys.process.ProcessLogger =
    scala.sys.process.ProcessLogger((s) => { m.subTask(prefix + ":\n " + s) })
}

abstract class IRunnableWithProgressAndError extends IRunnableWithProgress {
  var error : Option[String] = None 
  def run_or_fail(monitor : IProgressMonitor) : Boolean
  final def run(monitor : IProgressMonitor) = {
    try if(!run_or_fail(monitor)) error = Some("Error")
    catch { case e : Throwable => error = Some(e.getMessage) }
    finally { monitor.done() }
  }
}

class InstallVersionJob(val ver : OPAMRoot#Package#Version) extends IRunnableWithProgressAndError {
  def run_or_fail(monitor : IProgressMonitor) = {
    monitor.beginTask("Installing " + ver.getPackage.name + " " + ver.version, 1)
    val ok = ver.install(pin = false, LOG.log(monitor,"Installing"))
    monitor.worked(1)
    ok
  }
}
class InstallAnyJob(val pkg : OPAMRoot#Package) extends IRunnableWithProgressAndError  {
  def run_or_fail(monitor : IProgressMonitor) = {
    monitor.beginTask("Installing " + pkg.name, 1)
    val ok = pkg.installAnyVersion(LOG.log(monitor,"Installing"))
    monitor.worked(1)
    ok
  }
}
class UpdateJob(val r : OPAMRoot) extends IRunnableWithProgressAndError  {
  def run_or_fail(monitor : IProgressMonitor) = {
    monitor.beginTask("Updating repositories", 1)
    val ok = r.updateRepositories(LOG.log(monitor,"Updating"))
    monitor.worked(1)
    ok
  }
}
class UpgradeJob(val r : OPAMRoot) extends IRunnableWithProgressAndError  {
  def run_or_fail(monitor : IProgressMonitor) = {
    monitor.beginTask("Upgrading all packages", 1)
    val ok = r.upgradeAllPackages(LOG.log(monitor,"Upgrading"))
    monitor.worked(1)
    ok
  }
}
class RemoveJob(val pkg : OPAMRoot#Package) extends IRunnableWithProgressAndError  {
  def run_or_fail(monitor : IProgressMonitor) = {
    monitor.beginTask("Removing " + pkg.name, 1)
    pkg.getInstalledVersion.foreach(_.uninstall())
    monitor.worked(1)
    true
  }
}
class InitJob(val path : Path, val ocaml : String, val coq : String) extends IRunnableWithProgressAndError {
  def run_or_fail(monitor : IProgressMonitor) : Boolean = {
    monitor.beginTask("Initialise OPAM root", 7)
    
    val logger = LOG.log(monitor, _ : String)

    val r = OPAM.initRoot(path, ocaml, logger = logger("Initializing"))
    OPAMPreferences.Roots.set(OPAMPreferences.Roots.get() :+ path.toString)
    monitor.worked(1)

    // debugging: make a root without Coq
    if (coq == "no") return true
    
    r.addRepositories(logger("Adding repository: Coq released"),
        new r.Repository("coq","http://coq.inria.fr/opam/released"))
    monitor.worked(1)

    val dev_version = """.*dev$""".r  
    coq match {
      case dev_version() => 
        r.addRepositories(logger("Adding repository: Coq core-dev"),
          new r.Repository("core-dev","http://coq.inria.fr/opam/core-dev"))
      case _ => // no need for extra repos
    }
    monitor.worked(1)
        
    if (!r.getPackage("coq").getVersion(coq).install(true, logger("Building Coq")))
      throw new OPAMException("Coqoon needs Coq")
    monitor.worked(1)
    
    r.addRepositories(logger("Adding repository: PIDEtop"),
        new r.Repository("coqoon","http://bitbucket.org/coqpide/opam.git"))
    monitor.worked(1)

    if (!r.getPackage("pidetop").installAnyVersion(logger("Installing pidetop")))
      throw new OPAMException("Coqoon needs pidetop")
    monitor.worked(1)

    r.getPackage("ocamlbuild").installAnyVersion(logger("Installing ocamlbuild"))
    monitor.worked(1)

    true
  }
}

class OPAMRootDelete(path : String, s : org.eclipse.swt.widgets.Shell) extends Dialog(s) {
      var delete = false
  
      override def createDialogArea(c : widgets.Composite) = {
        this.getShell.setText("OPAM root deletion")

        val names = UIXML(
          <composite name="root">
           <grid-layout margin="10" columns="1" />
           <label>Removing OPAM root {path}</label>
           <button name="check" style="check">Remove directory from file system</button>
          </composite>,c)
          
         val check = names.get[widgets.Button]("check").get
         Listener.Selection(check, Listener {
          case Event.Selection(_) => delete = check.getSelection
         })
          
         names.get[widgets.Composite]("root").get
      }
}

class OPAMRootCreation(s : org.eclipse.swt.widgets.Shell)
  extends Dialog(s) {
  
    var path = ""
    var coq = ""
    var ocaml = ""
     
    override def createDialogArea(c : widgets.Composite) = {
      this.getShell.setText("OPAM root parameters")

      val names = UIXML(
        <composite name="root">
          <grid-layout margin="10" columns="3" />
          <label>Coq:<tool-tip>Coq version to install</tool-tip></label>
          <combo name="coq" border="true">
            <grid-data h-grab="true" h-span="2" />
          </combo>
          <label>OCaml:<tool-tip>OCaml version to install</tool-tip></label>
          <combo name="ocaml" border="true">
            <grid-data h-grab="true" h-span="2" />
          </combo>
          <label separator="horizontal">
            <grid-data h-grab="true" h-span="3" />
          </label>
          <label>Path:<tool-tip>Select a directory</tool-tip></label>
          <text name="path" border="true" />
          <button>
            <listener kind="select-directory" target="path" />
            Select...
          </button>
        </composite>,c)

      val wcoq = names.get[widgets.Combo]("coq").get
      Listener.Modify(wcoq, Listener {
        case Event.Modify(_) => coq = wcoq.getText.trim
      })
      wcoq.add("8.5.2")
      wcoq.add("8.6.1")
      wcoq.add("8.7.dev")
      
      val wocaml = names.get[widgets.Combo]("ocaml").get
      Listener.Modify(wocaml, Listener {
        case Event.Modify(_) => ocaml = wocaml.getText.trim
      })
      wocaml.add("4.01.0")
      wocaml.add("4.02.3")
      wocaml.add("system")
      
      val wpath = names.get[widgets.Text]("path").get
      Listener.Modify(wpath, Listener {
        case Event.Modify(_) => path = wpath.getText.trim
      })
      val updater = Listener {
        case Event.Modify(_) =>
          wpath.setText(System.getenv("HOME") + "/opam-roots/coq-" +
              wocaml.getText + "-" + wcoq.getText)
      }
      Listener.Modify(wcoq, updater)
      Listener.Modify(wocaml, updater)
      wcoq.select(0)
      wocaml.select(0)

      names.get[widgets.Composite]("root").get
    }
}

class OPAMPreferencesPage
    extends PreferencePage with IWorkbenchPreferencePage {
  noDefaultAndApplyButton()

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
          <combo-viewer name="cv0">
            <grid-data h-grab="true" />
          </combo-viewer>
          <button name="add">
            Add...
          </button>
          <button name="remove">
            Remove...
          </button>
          <label separator="horizontal">
            <grid-data h-span="4" />
          </label>
          <label>
            Location:
          </label>
          <label name="path">
            <grid-data h-span="3" />
          </label>
          <tab-folder>
            <grid-data h-span="4" grab="true" />
            <tab label="Repositories">
              <composite>
                <grid-layout columns="1" />
                <tree-viewer name="tv0">
                  <grid-data grab="true" />
                  <column style="left" label="Name" />
                  <column style="left" label="URI" />
                </tree-viewer>
                <composite>
                 <grid-layout columns="2" />
                  <button name="update" enabled="true">
                   Update repositories
                 </button>
                 <button name="upgrade" enabled="true">
                   Upgrade packages
                 </button>
                </composite>
             </composite>
            </tab>
            <tab label="Packages">
              <composite>
                <grid-layout columns="1" />
                <tree-viewer name="tv1">
                  <grid-data align="fill" grab="true" />
                  <column style="left" label="Name" />
                  <column style="left" label="Status" />
                </tree-viewer>
                <composite>
                 <grid-layout columns="2" />
                  <grid-data align="fill" h-grab="true" />
                  <button name="coq-only" style="check" enabled="true">
                  <grid-data align="fill" h-grab="true" />
                   Show only Coq packages
                 </button>
                 <button name="toggle" enabled="false">
                   Install..
                 </button>
                </composite>
              </composite>
            </tab>
          </tab-folder>
        </composite>, c)
        
    val Some(filter) = names.get[widgets.Button]("coq-only")
    filter.setSelection(true)
        
    val Some(cv0) = names.get[viewers.ComboViewer]("cv0")
    val Seq(Some(tv0), Some(tv1)) =
      names.getMany[viewers.TreeViewer]("tv0", "tv1")
    tv0.setContentProvider(new OPAMRepositoryContentProvider)
    tv0.setLabelProvider(new OPAMRepositoryLabelProvider)
    tv1.setContentProvider(new OPAMPackageContentProvider(filter))
    tv1.setLabelProvider(new OPAMPackageLabelProvider)
    viewers.ColumnViewerToolTipSupport.enableFor(tv1)
    Seq(tv0, tv1).foreach(tv => {
      tv.getTree.setLinesVisible(true)
      tv.getTree.setHeaderVisible(true)
      for (i <- tv.getTree.getColumns)
        i.pack
    })

    var job : Option[IRunnableWithProgressAndError] = None
    val toggle = names.get[widgets.Button]("toggle").get
    Listener.Selection(tv1.getControl, Listener {
      case Event.Selection(ev) =>
        tv1.getSelection match {
          case i : viewers.IStructuredSelection =>
            i.getFirstElement match {
              case p : OPAMRoot#Package if p.isPinned() =>
                toggle.setText("Package is pinned")
                toggle.setEnabled(false)
                job = None
              case v : OPAMRoot#Package#Version
                  if v.getPackage.isPinned() =>
                toggle.setText("Package is pinned")
                toggle.setEnabled(false)
                job = None
              case p : OPAMRoot#Package =>
                p.getInstalledVersion match {
                  case Some(v) =>
                    toggle.setText("Uninstall...")
                    toggle.setEnabled(true)
                    job = Some(new RemoveJob(p))
                  case None =>
                    toggle.setText("Install new package...")
                    toggle.setEnabled(true)
                    job = Some(new InstallAnyJob(p))
                }
              case v : OPAMRoot#Package#Version
                  if v.getPackage.getInstalledVersion.contains(v) =>
                toggle.setText("Uninstall...")
                toggle.setEnabled(true)
                job = Some(new RemoveJob(v.getPackage))
              case v : OPAMRoot#Package#Version
                  if v.getPackage.getInstalledVersion != None =>
                toggle.setText("Replace installed version...")
                toggle.setEnabled(true)
                job = Some(new InstallVersionJob(v))
              case v : OPAMRoot#Package#Version =>
                toggle.setText("Install new package...")
                toggle.setEnabled(true)
                job = Some(new InstallVersionJob(v))
            }
            import org.eclipse.swt.SWT
            toggle.pack
            toggle.getParent.layout()
          case _ =>
        }
    })
    Listener.Selection(toggle, Listener {
      case Event.Selection(ev) =>
        job.foreach(job => {
          val dialog =
            new org.eclipse.jface.dialogs.ProgressMonitorDialog(this.getShell)
          dialog.run(true, true, job)
          job.error match {
            case Some(s) => this.setErrorMessage(s)
            case None =>
              tv1.refresh()
          }
        })
    })
    val update = names.get[widgets.Button]("update").get
    Listener.Selection(update, Listener {
      case Event.Selection(ev) =>
        activeRoot.get.foreach(r => {
          val dialog =
            new org.eclipse.jface.dialogs.ProgressMonitorDialog(this.getShell)
          val job = new UpdateJob(OPAM.canonicalise(new Path(r)).get)
          dialog.run(true, true, job)
          job.error match {
            case Some(s) => this.setErrorMessage(s)
            case None => tv1.refresh()
          }
    })})
    val upgrade = names.get[widgets.Button]("upgrade").get
    Listener.Selection(upgrade, Listener {
      case Event.Selection(ev) =>
        activeRoot.get.foreach(r => {
          val dialog =
            new org.eclipse.jface.dialogs.ProgressMonitorDialog(this.getShell)
          val job = new UpgradeJob(OPAM.canonicalise(new Path(r)).get)
          dialog.run(true, true, job)
          job.error match {
            case Some(s) => this.setErrorMessage(s)
            case None => tv1.refresh()
          }
    })})
    
    Listener.Selection(filter, Listener {
      case Event.Selection(ev) => tv1.refresh()
    })
  
    cv0.setContentProvider(new OPAMContentProvider)
    cv0.setLabelProvider(new OPAMLabelProvider)
    cv0.setInput(OPAM)
    Listener.Selection(cv0.getControl, Listener {
      case Event.Selection(ev) =>
        cv0.getSelection match {
          case i : viewers.IStructuredSelection =>
            val root = i.getFirstElement.asInstanceOf[OPAMRoot]
            activeRoot.set(Some(Some(root.path.toString)))
            names.get[widgets.Label]("path").foreach(
                _.setText(root.path.toString))
            Seq(tv0, tv1).foreach(tv => {
              tv.setInput(root)
              for (i <- tv.getTree.getColumns)
                i.pack
            })
          case _ =>
        }
    })
    val remove = names.get[widgets.Button]("remove").get
    Listener.Selection(remove, Listener {
      case Event.Selection(ev) => {
        val selected = cv0.getSelection.asInstanceOf[viewers.IStructuredSelection].getFirstElement.asInstanceOf[OPAMRoot].path.toString
        val d = new OPAMRootDelete(selected, this.getShell)
        if (d.open() == org.eclipse.jface.window.Window.OK) {
          // if (d.delete) FIXME
          OPAMPreferences.Roots.set(OPAMPreferences.Roots.get().filter(x => x != selected))
        }
    }})
    val button = names.get[widgets.Button]("add").get
    val shell = button.getShell
    Listener.Selection(button, Listener {
      case Event.Selection(ev) =>
        val d = new OPAMRootCreation(button.getShell)
        if (d.open() == org.eclipse.jface.window.Window.OK) {
          val create_root = d.coq != "" && d.ocaml != "" && d.path != ""
          if (create_root) try {
              val rootPath = new Path(d.path)
              val op = new InitJob(rootPath,d.ocaml,d.coq)
              val dialog = new org.eclipse.jface.dialogs.ProgressMonitorDialog(this.getShell)
              dialog.run(true, true, op)
              op.error match {
                case Some(s) => this.setErrorMessage(s)
                case None =>
                  cv0.refresh(OPAM)
                  cv0.setSelection(new viewers.StructuredSelection(
                      OPAM.canonicalise(rootPath)), true)
              }
            } catch {
              case e : java.lang.reflect.InvocationTargetException =>
                this.setErrorMessage(e.getMessage)
              case e : InterruptedException =>
                this.setErrorMessage(e.getMessage)
            }
        }
    })

    activeRoot.get.foreach(r => {
      cv0.setSelection(new viewers.StructuredSelection(
          OPAM.canonicalise(new Path(r))), true)
    })

    names.get[widgets.Composite]("root").get
  }
}

import dk.itu.coqoon.ui.loadpath.FuturisticContentProvider

class OPAMContentProvider extends FuturisticContentProvider {
  override def actuallyGetChildren(i : AnyRef) =
    i match {
      case i @ dk.itu.coqoon.opam.OPAM =>
        OPAMPreferences.Roots.get.map(new Path(_)).flatMap(OPAM.canonicalise)
      case _ => Seq()
    }
}

class OPAMRepositoryContentProvider extends FuturisticContentProvider {
  override def actuallyGetChildren(i : AnyRef) =
    i match {
      case r : OPAMRoot =>
        r.getRepositories
      case _ => Seq()
    }
}

class OPAMPackageContentProvider(val filter : widgets.Button)
  extends FuturisticContentProvider {
  override def actuallyGetChildren(i : AnyRef) =
    i match {
      case r : OPAMRoot =>
        val s : String = ""
        r.getPackages(p =>
          !filter.getSelection() ||
          p.name.startsWith("coq-") ||
          p.name == "coq" || p.name == "pidetop")
      case p : OPAMRoot#Package =>
        p.getAvailableVersions
      case _ => Seq()
    }
  override def hasChildren(i : AnyRef) =
    i match {
      case p : OPAMRoot#Package =>
        true
      case _ =>
        super.hasChildren(i)
    }
}

class OPAMLabelProvider
    extends viewers.BaseLabelProvider with viewers.ILabelProvider {
  override def getImage(i : AnyRef) = null
  override def getText(i : AnyRef) =
    i match {
      case i @ dk.itu.coqoon.opam.OPAM =>
        "OPAM"
      case r : dk.itu.coqoon.opam.OPAMRoot =>
        r.path.lastSegment()
      case _ => null
    }
}

class OPAMRepositoryLabelProvider
    extends viewers.BaseLabelProvider with viewers.ITableLabelProvider {
  override def getColumnImage(i : AnyRef, column : Int) = null
  override def getColumnText(i : AnyRef, column : Int) =
    (i, column) match {
      case (i : OPAMRoot#Repository, 0) =>
        i.name
      case (i : OPAMRoot#Repository, 1) =>
        i.uri
      case _ => null
    }
}

class OPAMPackageLabelProvider
    extends viewers.ColumnLabelProvider with viewers.ITableLabelProvider {
  override def getColumnImage(i : AnyRef, column : Int) = null
  override def getColumnText(i : AnyRef, column : Int) =
    (i, column) match {
      case (i : OPAMRoot#Package, 0) =>
        i.name
      case (i : OPAMRoot#Package, 1) =>
        i.getInstalledVersion match {
          case Some(i.Version(v)) =>
            v
          case _ =>
            null
        }
      case (i : OPAMRoot#Package#Version, 0) =>
        i.version
      case (i : OPAMRoot#Package#Version, 1)
          if i.getPackage.getInstalledVersion.contains(i) =>
        "installed"
      case _ => null
    }
  override def getToolTipText(i : AnyRef) =
    i match { case i : OPAMRoot#Package => i.getDescription }
}

object OPAMPreferences {
  object Roots {
    final val ID = "roots"
    def get() =
      Activator.getDefault.getPreferenceStore.getString(ID).split(";").filter(
          !_.isEmpty)
    def set(roots : Seq[String]) =
      if (roots.forall(!_.contains(";"))) {
        Activator.getDefault.getPreferenceStore().setValue(
            ID, roots.filterNot(_.isEmpty).distinct.mkString(";"))
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

    import dk.itu.coqoon.core.CoqoonPreferences
    def set(root : String) =
      if (Roots.get.contains(root)) {
        Activator.getDefault.getPreferenceStore.setValue(ID, root)
        OPAM.canonicalise(new Path(root)) foreach {
          case root =>
            CoqoonPreferences.CoqPath.set(
                new Path(root.getPackage("coq").getConfigVar("bin")))
        }
      }
  }
}
