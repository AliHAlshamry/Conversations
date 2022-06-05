package eu.siacs.chatx.xmpp;

import eu.siacs.chatx.crypto.axolotl.AxolotlService;

public interface OnKeyStatusUpdated {
	void onKeyStatusUpdated(AxolotlService.FetchStatus report);
}
