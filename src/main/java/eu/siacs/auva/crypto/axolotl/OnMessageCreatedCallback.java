package eu.siacs.auva.crypto.axolotl;

public interface OnMessageCreatedCallback {
	void run(XmppAxolotlMessage message);
}
