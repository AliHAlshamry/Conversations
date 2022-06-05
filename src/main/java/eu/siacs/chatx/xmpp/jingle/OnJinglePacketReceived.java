package eu.siacs.chatx.xmpp.jingle;

import eu.siacs.chatx.entities.Account;
import eu.siacs.chatx.xmpp.PacketReceived;
import eu.siacs.chatx.xmpp.jingle.stanzas.JinglePacket;

public interface OnJinglePacketReceived extends PacketReceived {
	void onJinglePacketReceived(Account account, JinglePacket packet);
}
