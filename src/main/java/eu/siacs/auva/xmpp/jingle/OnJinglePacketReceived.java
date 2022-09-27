package eu.siacs.auva.xmpp.jingle;

import eu.siacs.auva.entities.Account;
import eu.siacs.auva.xmpp.PacketReceived;
import eu.siacs.auva.xmpp.jingle.stanzas.JinglePacket;

public interface OnJinglePacketReceived extends PacketReceived {
	void onJinglePacketReceived(Account account, JinglePacket packet);
}
