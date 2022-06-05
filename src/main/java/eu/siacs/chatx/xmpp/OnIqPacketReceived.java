package eu.siacs.chatx.xmpp;

import eu.siacs.chatx.entities.Account;
import eu.siacs.chatx.xmpp.stanzas.IqPacket;

public interface OnIqPacketReceived extends PacketReceived {
	void onIqPacketReceived(Account account, IqPacket packet);
}
