package eu.siacs.chatx.xmpp.jingle;

import eu.siacs.chatx.entities.Account;
import eu.siacs.chatx.xmpp.Jid;

public interface OngoingRtpSession {
    Account getAccount();
    Jid getWith();
    String getSessionId();
}
