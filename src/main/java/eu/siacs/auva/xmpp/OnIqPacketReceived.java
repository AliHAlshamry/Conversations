package eu.siacs.auva.xmpp;

import eu.siacs.auva.entities.Account;
import eu.siacs.auva.xmpp.stanzas.IqPacket;

public interface OnIqPacketReceived extends PacketReceived {
	void onIqPacketReceived(Account account, IqPacket packet);
}
