package eu.siacs.auva.xmpp.jingle;

public interface OnTransportConnected {
	void failed();

	void established();
}
