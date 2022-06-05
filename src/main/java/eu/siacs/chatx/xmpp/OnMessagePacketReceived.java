package eu.siacs.chatx.xmpp;

import eu.siacs.chatx.entities.Account;
import eu.siacs.chatx.xmpp.stanzas.MessagePacket;

public interface OnMessagePacketReceived extends PacketReceived {
	void onMessagePacketReceived(Account account, MessagePacket packet);
}
