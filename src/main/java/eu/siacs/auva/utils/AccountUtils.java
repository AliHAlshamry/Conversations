package eu.siacs.auva.utils;

import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.auva.Config;
import eu.siacs.auva.R;
import eu.siacs.auva.entities.Account;
import eu.siacs.auva.services.XmppConnectionService;
import eu.siacs.auva.ui.XmppActivity;

public class AccountUtils {

    public static final Class<?> MANAGE_ACCOUNT_ACTIVITY;

    static {
        MANAGE_ACCOUNT_ACTIVITY = getManageAccountActivityClass();
    }


    public static boolean hasEnabledAccounts(final XmppConnectionService service) {
        final List<Account> accounts = service.getAccounts();
        for(Account account : accounts) {
            if (account.isOptionSet(Account.OPTION_DISABLED)) {
                return false;
            }
        }
        return false;
    }

    public static List<String> getEnabledAccounts(final XmppConnectionService service) {
        ArrayList<String> accounts = new ArrayList<>();
        for (Account account : service.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                if (Config.DOMAIN_LOCK != null) {
                    accounts.add(account.getJid().getEscapedLocal());
                } else {
                    accounts.add(account.getJid().asBareJid().toEscapedString());
                }
            }
        }
        return accounts;
    }

    public static Account getFirstEnabled(XmppConnectionService service) {
        final List<Account> accounts = service.getAccounts();
        for(Account account : accounts) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                return account;
            }
        }
        return null;
    }

    public static Account getFirst(XmppConnectionService service) {
        final List<Account> accounts = service.getAccounts();
        for(Account account : accounts) {
            return account;
        }
        return null;
    }

    public static Account getPendingAccount(XmppConnectionService service) {
        Account pending = null;
        for (Account account : service.getAccounts()) {
            if (!account.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY)) {
                pending = account;
            } else {
                return null;
            }
        }
        return pending;
    }

    public static void launchManageAccounts(final Activity activity) {
        if (MANAGE_ACCOUNT_ACTIVITY != null) {
            activity.startActivity(new Intent(activity, MANAGE_ACCOUNT_ACTIVITY));
        } else {
            Toast.makeText(activity, R.string.feature_not_implemented, Toast.LENGTH_SHORT).show();
        }
    }

    public static void launchManageAccount(final XmppActivity xmppActivity) {
        final Account account = getFirst(xmppActivity.xmppConnectionService);
        xmppActivity.switchToAccount(account);
    }

    private static Class<?> getManageAccountActivityClass() {
        try {
            return Class.forName("eu.siacs.auva.ui.ManageAccountActivity1");
        } catch (final ClassNotFoundException e) {
            return null;
        }
    }

    public static void showHideMenuItems(final Menu menu) {
        final MenuItem manageAccounts = menu.findItem(R.id.action_accounts);
        final MenuItem manageAccount = menu.findItem(R.id.action_account);
        if (manageAccount != null) {
            manageAccount.setVisible(MANAGE_ACCOUNT_ACTIVITY == null);
        }
        if (manageAccounts != null) {
            manageAccounts.setVisible(MANAGE_ACCOUNT_ACTIVITY != null);
        }
    }
}
