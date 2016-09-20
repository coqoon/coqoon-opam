package dk.itu.coqoon.opam.ui

import dk.itu.coqoon.ui.utilities.UIXML
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.swt.widgets.Composite
import org.eclipse.jface.preference.PreferencePage

class OPAMPreferencesPage
    extends PreferencePage with IWorkbenchPreferencePage {
  override def init(w : IWorkbench) = ()
  override def createContents(c : Composite) = {
    val names = UIXML(
        <composite name="root">
          <grid-layout columns="4" />
          <label>
            Root:
          </label>
          <combo>
            <grid-data h-grab="true" />
          </combo>
          <button>
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
    names.get[Composite]("root").get
  }
}