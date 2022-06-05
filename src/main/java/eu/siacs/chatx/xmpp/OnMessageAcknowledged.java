package eu.siacs.chatx.xmpp;

import eu.siacs.chatx.entities.Account;

public interface OnMessageAcknowledged {
    boolean onMessageAcknowledged(Account account, Jid to, String id);
}
