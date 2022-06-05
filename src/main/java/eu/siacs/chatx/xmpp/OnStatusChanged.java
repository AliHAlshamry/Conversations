package eu.siacs.chatx.xmpp;

import eu.siacs.chatx.entities.Account;

public interface OnStatusChanged {
	void onStatusChanged(Account account);
}
