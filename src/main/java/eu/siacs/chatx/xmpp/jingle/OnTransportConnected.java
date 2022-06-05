package eu.siacs.chatx.xmpp.jingle;

public interface OnTransportConnected {
	void failed();

	void established();
}
