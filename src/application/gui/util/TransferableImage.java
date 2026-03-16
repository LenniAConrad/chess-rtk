package application.gui.util;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;

/**
 * Clipboard wrapper for transferring a BufferedImage.
 *
 * Implements {@link Transferable} and {@link ClipboardOwner} so captured board snapshots can be copied into the system clipboard as images.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class TransferableImage implements Transferable, ClipboardOwner {
	/**
	 * image field.
	 */
	private final BufferedImage image;

	/**
	 * Creates a transferable image payload.
	 *
	 * @param image image to expose via the clipboard.
	 */
	public TransferableImage(BufferedImage image) {
		this.image = image;
	}

	@Override
	/**
	 * getTransferDataFlavors method.
	 *
	 * @return return value.
	 */
	public DataFlavor[] getTransferDataFlavors() {
		return new DataFlavor[] { DataFlavor.imageFlavor };
	}

	@Override
	/**
	 * isDataFlavorSupported method.
	 *
	 * @param flavor parameter.
	 * @return return value.
	 */
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return DataFlavor.imageFlavor.equals(flavor);
	}

	@Override
	/**
	 * {@inheritDoc}
	 *
	 * @param flavor requested flavor.
	 * @return the buffered image when the flavor is supported.
	 * @throws UnsupportedFlavorException when the flavor is not supported.
	 */
	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
		if (!isDataFlavorSupported(flavor)) {
			throw new UnsupportedFlavorException(flavor);
		}
		return image;
	}

	@Override
	/**
	 * lostOwnership method.
	 *
	 * @param clipboard parameter.
	 * @param contents parameter.
	 */
	public void lostOwnership(Clipboard clipboard, Transferable contents) {
		// no-op
	}
}
