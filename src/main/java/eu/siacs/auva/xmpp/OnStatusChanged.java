package eu.siacs.auva.xmpp;

import eu.siacs.auva.entities.Account;

public interface OnStatusChanged {
	void onStatusChanged(Account account);
}
