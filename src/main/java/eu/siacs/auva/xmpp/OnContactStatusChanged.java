package eu.siacs.auva.xmpp;

import eu.siacs.auva.entities.Contact;

public interface OnContactStatusChanged {
	void onContactStatusChanged(final Contact contact, final boolean online);
}
