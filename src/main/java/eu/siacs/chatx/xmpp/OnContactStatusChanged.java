package eu.siacs.chatx.xmpp;

import eu.siacs.chatx.entities.Contact;

public interface OnContactStatusChanged {
	void onContactStatusChanged(final Contact contact, final boolean online);
}
