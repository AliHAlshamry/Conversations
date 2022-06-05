package eu.siacs.chatx.xmpp;

import eu.siacs.chatx.entities.Account;

public interface OnAdvancedStreamFeaturesLoaded {
	void onAdvancedStreamFeaturesAvailable(final Account account);
}
