package eu.siacs.auva.xmpp.jingle;

import eu.siacs.auva.entities.DownloadableFile;

public interface OnFileTransmissionStatusChanged {
	void onFileTransmitted(DownloadableFile file);

	void onFileTransferAborted();
}
