package eu.siacs.chatx.entities;

import eu.siacs.chatx.xmpp.Jid;

public interface Blockable {
	boolean isBlocked();
	boolean isDomainBlocked();
	Jid getBlockedJid();
	Jid getJid();
	Account getAccount();
}
