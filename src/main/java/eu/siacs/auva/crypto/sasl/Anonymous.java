package eu.siacs.auva.crypto.sasl;

import java.security.SecureRandom;

import eu.siacs.auva.entities.Account;
import eu.siacs.auva.xml.TagWriter;

public class Anonymous extends SaslMechanism {

    public static final String MECHANISM = "ANONYMOUS";

    public Anonymous(TagWriter tagWriter, Account account, SecureRandom rng) {
        super(tagWriter, account, rng);
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }

    @Override
    public String getClientFirstMessage() {
        return "";
    }
}
