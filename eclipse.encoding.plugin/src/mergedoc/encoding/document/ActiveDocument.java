package mergedoc.encoding.document;

import static java.lang.String.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.IEncodingSupport;
import org.eclipse.ui.internal.WorkbenchWindow;

import mergedoc.encoding.Activator;
import mergedoc.encoding.Charsets;
import mergedoc.encoding.IActiveDocumentAgentCallback;
import mergedoc.encoding.PackageRoot;

/**
 * This document handles editors which support IEncodingSupport for ActiveDocumentAgent.
 * @author Tsoi Yat Shing
 * @author Shinji Kashihara
 */
@SuppressWarnings("restriction")
public class ActiveDocument {

	protected IActiveDocumentAgentCallback callback;
	protected IEditorPart editor;
	protected String lineSeparator;
	protected IEncodingSupport encodingSupport;

	protected String currentEncoding;
	protected String inheritedEncoding;
	protected String detectedCharset;
	protected String contentTypeEncoding;
	protected String contentCharset;

	public ActiveDocument(IEditorPart editor, IActiveDocumentAgentCallback callback) {
		init(editor, callback);
		infoMessage(null);
	}

	protected void init(IEditorPart editor, IActiveDocumentAgentCallback callback) {

		this.editor = editor;
		this.callback = callback;
		if (editor == null) throw new IllegalArgumentException("editor must not be null.");
		if (callback == null) throw new IllegalArgumentException("callback must not be null.");

		this.encodingSupport = editor.getAdapter(IEncodingSupport.class);
		if (encodingSupport == null) throw new IllegalArgumentException("editor must provide IEncodingSupport.");

		updateEncoding();
	}

	/**
	 * Get the editor associated with this handler.
	 * If the associated editor is different from the active editor, ActiveDocumentAgent will change handler.
	 */
	public IEditorPart getEditor() {
		return editor;
	}
	public IProject getProject() {
		return null;
	}
	public PackageRoot getPackageRoot() {
		return null;
	}
	public IFile getFile() {
		return null;
	}
	public String getFilePropertiesEncoding() {
		return null;
	}
	public IContentDescription getContentDescription() {
		return null;
	}
	public byte[] getContentDescriptionBOM() {
		IContentDescription cd = getContentDescription();
		return (byte[]) (cd == null ? null : cd.getProperty(IContentDescription.BYTE_ORDER_MARK));
	}

	/**
	 * Get the name of the active document, if supported by the editor and the editor input.
	 * @return the name or null.
	 */
	public String getFileName() {
		return editor.getEditorInput().getName();
	}
	public String getLineSeparator() {
		return lineSeparator;
	}

	/**
	 * Get the encoding setting of the active document, if supported by the editor.
	 * @return the encoding setting or null.
	 */
	public String getCurrentEncoding() {
		return currentEncoding;
	}
	public String getCurrentEncodingLabel() {
		if (currentEncoding == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(currentEncoding);
		if (isUTFEncoding()) {
			byte[] bom = getContentDescriptionBOM();
			if (bom != null) {
				if (bom == IContentDescription.BOM_UTF_16BE) {
					sb.append(" BE");
				} else if (bom == IContentDescription.BOM_UTF_16LE) {
					sb.append(" LE");
				}
				sb.append(" BOM");
			}
		}
		return sb.toString();
	}
	public boolean isUTFEncoding() {
		return currentEncoding != null && currentEncoding.startsWith("UTF-");
	}
	public boolean hasBOM() {
		return isUTFEncoding() && getContentDescriptionBOM() != null;
	}
	public boolean canAddBOM() {
		return currentEncoding != null && currentEncoding.matches("UTF-(8|16|16BE|16LE)");
	}

	public String getInheritedEncoding() {
		return inheritedEncoding;
	}
	public String getDetectedCharset() {
		return detectedCharset;
	}
	public String getContentTypeEncoding() {
		return contentTypeEncoding;
	}
	public String getContentCharset() {
		return contentCharset;
	}

	public boolean matchesEncoding() {
		return detectedCharset != null && Charsets.equals(detectedCharset, currentEncoding);
	}
	public boolean mismatchesEncoding() {
		return detectedCharset != null && !Charsets.equals(detectedCharset, currentEncoding);
	}

	public void propertyChanged(Object source, int propId) {
		// It seems that the editor's encoding will not change when it is dirty
		if (!editor.isDirty()) {
			// The document may be just saved.
			updateEncodingChange();
		}
	}

	public void resourceChanged(IResourceChangeEvent event) {
		// It seems that propertyChanged() can detect changes well already
	}

	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		// It seems that propertyChanged() can detect encoding setting changes well already
	}

	/**
	 * Set the encoding of the active document, if supported by the editor.
	 */
	public void setEncoding(String encoding) {

		String contentCharset = null;
		IContentDescription contentDescription = getContentDescription();
		if (contentDescription != null) {
			contentCharset = contentDescription.getCharset();
		}
		if (contentCharset != null) {
			if (Charsets.equals(encoding, contentCharset)) {
				encoding = null;
			}
		} else if (Charsets.equals(encoding, inheritedEncoding)) {
			encoding = null;
		}
		try {
			// Null is clear for inheritance
			encodingSupport.setEncoding(encoding);
		} catch (Exception e) {
			// Ignore BackingStoreException for not sync project preferences store
			Activator.info("Failed set encoding", e);
		}
		updateEncodingChange();
	}

	protected final void updateEncodingChange() {

		String currentEncodingOld = currentEncoding;
		String detectedCharsetOld = detectedCharset;
		String contentCharsetOld = contentCharset;
		String lineSeparatorOld = lineSeparator;

		updateEncoding();

		if (
			!StringUtils.equals(currentEncodingOld, currentEncoding) ||
			!StringUtils.equals(detectedCharsetOld, detectedCharset) ||
			!StringUtils.equals(contentCharsetOld, contentCharset) ||
			!StringUtils.equals(lineSeparatorOld, lineSeparator)
		) {
			// Invoke the callback if the encoding information is changed
			callback.encodingChanged();
		}
	}

	/**
	 * Update the encoding information in member variables.
	 * This method may be overrided, but should be called by the sub-class.
	 */
	protected void updateEncoding() {

		currentEncoding = null;
		inheritedEncoding = null;
		detectedCharset = null;
		contentTypeEncoding = null;
		lineSeparator = null;

		if (encodingSupport != null) {
			currentEncoding = encodingSupport.getEncoding();
			if (currentEncoding == null) {
				// workspace encoding
				currentEncoding = encodingSupport.getDefaultEncoding();
			}
		}
	}

	public boolean canChangeFileEncoding() {
		return false;
	}
	public boolean canConvertLineSeparator() {
		return false;
	}
	public boolean enabledContentType() {
		return false;
	}

	protected InputStream getInputStream() {
		throw new UnsupportedOperationException("Non implements getInputStream method.");
	}
	protected String getContentString() {
		InputStream inputStream = getInputStream();
		try {
			return IOUtils.toString(inputStream, getCurrentEncoding());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		} finally {
			IOUtils.closeQuietly(inputStream);
		}
	}

	protected void setContents(byte[] bytes) {
		throw new UnsupportedOperationException("Non implements setContents method.");
	}
	protected void setContents(String content, String storeEncoding) {
		try {
			setContents(content.getBytes(storeEncoding));
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	public void addBOM() {
		if (!hasBOM() && currentEncoding != null) {
			InputStream inputStream = getInputStream();
			try {
				if (inputStream != null) {
					byte[] bytes = IOUtils.toByteArray(getInputStream());
					byte[] newBytes = null;
					if (currentEncoding.equals("UTF-8")) {
						newBytes = ArrayUtils.addAll(IContentDescription.BOM_UTF_8, bytes);
					} else if (currentEncoding.matches("UTF-16(|BE)")) {
						newBytes = ArrayUtils.addAll(IContentDescription.BOM_UTF_16BE, bytes);
					} else if (currentEncoding.equals("UTF-16LE")) {
						newBytes = ArrayUtils.addAll(IContentDescription.BOM_UTF_16LE, bytes);
					} else {
						// Unsupport UTF-32
						throw new IllegalStateException("Encoding must be UTF-8 or UTF-16.");
					}
					setContents(newBytes);
					setEncoding(null); // detemined from BOM content
				}
			} catch (IOException e) {
				throw new IllegalStateException(e);
			} finally {
				IOUtils.closeQuietly(inputStream);
			}
		}
	}

	public void removeBOM() {
		InputStream inputStream = getInputStream();
		try {
			byte[] bytes = IOUtils.toByteArray(getInputStream());
			byte[] head3 = ArrayUtils.subarray(bytes, 0, 3);
			if (Arrays.equals(head3, IContentDescription.BOM_UTF_8)) {
				setContents(ArrayUtils.subarray(bytes, 3, Integer.MAX_VALUE));
				setEncoding("UTF-8"); // null if default
			} else {
				byte[] head2 = ArrayUtils.subarray(bytes, 0, 2);
				if (Arrays.equals(head2, IContentDescription.BOM_UTF_16BE)) {
					setContents(ArrayUtils.subarray(bytes, 2, Integer.MAX_VALUE));
					setEncoding("UTF-16BE");
				} else if (Arrays.equals(head2, IContentDescription.BOM_UTF_16LE)) {
					setContents(ArrayUtils.subarray(bytes, 2, Integer.MAX_VALUE));
					setEncoding("UTF-16LE");
				}
			}
			// Unsupport UTF-32
		} catch (IOException e) {
			throw new IllegalStateException(e);
		} finally {
			IOUtils.closeQuietly(inputStream);
		}
	}

	public void convertCharset(String newEncoding) {
		String content = getContentString();
		setContents(content, newEncoding);
		setEncoding(newEncoding);
	}
	public void setLineSeparator(String newLineSeparator) {
		if (newLineSeparator.equals(lineSeparator)) {
			return;
		}
		String newSeparator = "\r\n";
		if (newLineSeparator.equals("CR")) {
			newSeparator = "\r";
		} else if (newLineSeparator.equals("LF")) {
			newSeparator = "\n";
		}
		String content = getContentString().replaceAll("(\\r\\n|\\r|\\n)", newSeparator);
		setContents(content, getCurrentEncoding());
	}

	public void infoMessage(String message, Object... args) {
		setMessage("info", message, args);
	}
	public void warnMessage(String message, Object... args) {
		setMessage("warn", message, args);
	}
	public void warnDirtyMessage(boolean showsWarn) {
		if (showsWarn) {
			warnMessage("Editor must be saved before status bar action.");
		}
	}

	private void setMessage(String imageIconKey, String message, Object... args) {
		IStatusLineManager statusLineManager = null;
		if (editor == null) {
			WorkbenchWindow window = (WorkbenchWindow) PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			statusLineManager = window.getActionBars().getStatusLineManager();
		} else {
			statusLineManager = editor.getEditorSite().getActionBars().getStatusLineManager();
		}
		if (statusLineManager != null) {
			if (message == null) {
				statusLineManager.setMessage(null);
			} else {
				statusLineManager.setMessage(Activator.getImage(imageIconKey), format(message, args));
			}
		}
	}
}
