package eu.siacs.auva.xmpp.jingle;

import eu.siacs.auva.entities.Account;
import eu.siacs.auva.xmpp.Jid;

public interface OngoingRtpSession {
    Account getAccount();
    Jid getWith();
    String getSessionId();
}
