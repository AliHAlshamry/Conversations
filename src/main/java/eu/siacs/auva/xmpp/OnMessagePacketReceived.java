package eu.siacs.auva.xmpp;

import eu.siacs.auva.entities.Account;
import eu.siacs.auva.xmpp.stanzas.MessagePacket;

public interface OnMessagePacketReceived extends PacketReceived {
	void onMessagePacketReceived(Account account, MessagePacket packet);
}
