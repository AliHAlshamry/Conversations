package eu.siacs.auva.xmpp;

import eu.siacs.auva.entities.Account;
import eu.siacs.auva.xmpp.stanzas.PresencePacket;

public interface OnPresencePacketReceived extends PacketReceived {
	void onPresencePacketReceived(Account account, PresencePacket packet);
}
