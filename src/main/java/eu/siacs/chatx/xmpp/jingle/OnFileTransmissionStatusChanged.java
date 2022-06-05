package eu.siacs.chatx.xmpp.jingle;

import eu.siacs.chatx.entities.DownloadableFile;

public interface OnFileTransmissionStatusChanged {
	void onFileTransmitted(DownloadableFile file);

	void onFileTransferAborted();
}
