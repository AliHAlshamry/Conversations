package eu.siacs.auva.xmpp;

import eu.siacs.auva.entities.Account;

public interface OnMessageAcknowledged {
    boolean onMessageAcknowledged(Account account, Jid to, String id);
}
