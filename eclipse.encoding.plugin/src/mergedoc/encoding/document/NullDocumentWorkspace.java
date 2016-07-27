package mergedoc.encoding.document;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.ui.IEditorPart;

import mergedoc.encoding.IActiveDocumentAgentCallback;
import mergedoc.encoding.LineSeparators;

/**
 * This is not opend editor in workspace handler for ActiveDocumentAgent.
 * @author Shinji Kashihara
 */
public class NullDocumentWorkspace extends NullDocument {

	public NullDocumentWorkspace() {
	}

	@Override
	protected void init(IEditorPart editor, IActiveDocumentAgentCallback callback) {
		updateEncoding();
	}

	@Override
	protected void updateEncoding() {
		// Workspace preferences, not support project prefences
		currentEncoding = ResourcesPlugin.getEncoding();
		lineSeparator = LineSeparators.ofWorkspace();
	}

	@Override
	public void propertyChanged(Object source, int propId) {
		updateEncodingChange();
	}
}
