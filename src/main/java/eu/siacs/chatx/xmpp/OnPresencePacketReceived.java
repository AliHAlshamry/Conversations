package eu.siacs.chatx.xmpp;

import eu.siacs.chatx.entities.Account;
import eu.siacs.chatx.xmpp.stanzas.PresencePacket;

public interface OnPresencePacketReceived extends PacketReceived {
	void onPresencePacketReceived(Account account, PresencePacket packet);
}
