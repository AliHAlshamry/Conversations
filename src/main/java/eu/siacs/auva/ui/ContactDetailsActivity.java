package eu.siacs.auva.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.databinding.DataBindingUtil;

import org.openintents.openpgp.util.OpenPgpUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import eu.siacs.auva.Config;
import eu.siacs.auva.R;
import eu.siacs.auva.crypto.axolotl.AxolotlService;
import eu.siacs.auva.crypto.axolotl.FingerprintStatus;
import eu.siacs.auva.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.auva.databinding.ActivityContactDetailsBinding;
import eu.siacs.auva.databinding.DialogDeleteContactBinding;
import eu.siacs.auva.entities.Account;
import eu.siacs.auva.entities.Contact;
import eu.siacs.auva.entities.ListItem;
import eu.siacs.auva.services.AbstractQuickConversationsService;
import eu.siacs.auva.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.auva.services.XmppConnectionService.OnRosterUpdate;
import eu.siacs.auva.ui.adapter.MediaAdapter;
import eu.siacs.auva.ui.interfaces.OnMediaLoaded;
import eu.siacs.auva.ui.util.Attachment;
import eu.siacs.auva.ui.util.AvatarWorkerTask;
import eu.siacs.auva.ui.util.GridManager;
import eu.siacs.auva.ui.util.JidDialog;
import eu.siacs.auva.ui.util.MenuDoubleTabUtil;
import eu.siacs.auva.utils.Compatibility;
import eu.siacs.auva.utils.Emoticons;
import eu.siacs.auva.utils.IrregularUnicodeDetector;
import eu.siacs.auva.utils.PhoneNumberUtilWrapper;
import eu.siacs.auva.utils.UIHelper;
import eu.siacs.auva.utils.XmppUri;
import eu.siacs.auva.xml.Namespace;
import eu.siacs.auva.xmpp.Jid;
import eu.siacs.auva.xmpp.OnKeyStatusUpdated;
import eu.siacs.auva.xmpp.OnUpdateBlocklist;
import eu.siacs.auva.xmpp.XmppConnection;

public class ContactDetailsActivity extends OmemoActivity implements OnAccountUpdate, OnRosterUpdate, OnUpdateBlocklist, OnKeyStatusUpdated, OnMediaLoaded {
    public static final String ACTION_VIEW_CONTACT = "view_contact";
    private final int REQUEST_SYNC_CONTACTS = 0x28cf;
    ActivityContactDetailsBinding binding;
    private MediaAdapter mMediaAdapter;

    private Contact contact;
    private final DialogInterface.OnClickListener removeFromRoster = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            xmppConnectionService.deleteContactOnServer(contact);
        }
    };
    private final OnCheckedChangeListener mOnSendCheckedChange = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                    xmppConnectionService.stopPresenceUpdatesTo(contact);
                } else {
                    contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
                }
            } else {
                contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
                xmppConnectionService.sendPresencePacket(contact.getAccount(), xmppConnectionService.getPresenceGenerator().stopPresenceUpdatesTo(contact));
            }
        }
    };
    private final OnCheckedChangeListener mOnReceiveCheckedChange = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                xmppConnectionService.sendPresencePacket(contact.getAccount(), xmppConnectionService.getPresenceGenerator().requestPresenceUpdatesFrom(contact));
            } else {
                xmppConnectionService.sendPresencePacket(contact.getAccount(), xmppConnectionService.getPresenceGenerator().stopPresenceUpdatesFrom(contact));
            }
        }
    };
    private Jid accountJid;
    private Jid contactJid;
    private boolean showDynamicTags = false;
    private boolean showLastSeen = false;
    private boolean showInactiveOmemo = false;
    private String messageFingerprint;

    private void checkContactPermissionAndShowAddDialog() {
        if (hasContactsPermission()) {
            showAddToPhoneBookDialog();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_SYNC_CONTACTS);
        }
    }

    private boolean hasContactsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void showAddToPhoneBookDialog() {
        final Jid jid = contact.getJid();
        final boolean quicksyContact = AbstractQuickConversationsService.isQuicksy()
                && Config.QUICKSY_DOMAIN.equals(jid.getDomain())
                && jid.getLocal() != null;
        final String value;
        if (quicksyContact) {
            value = PhoneNumberUtilWrapper.toFormattedPhoneNumber(this, jid);
        } else {
            value = jid.toEscapedString();
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.action_add_phone_book));
        builder.setMessage(getString(R.string.add_phone_book_text, value));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.add), (dialog, which) -> {
            final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            if (quicksyContact) {
                intent.putExtra(Intents.Insert.PHONE, value);
            } else {
                intent.putExtra(Intents.Insert.IM_HANDLE, value);
                intent.putExtra(Intents.Insert.IM_PROTOCOL, CommonDataKinds.Im.PROTOCOL_JABBER);
                //TODO for modern use we want PROTOCOL_CUSTOM and an extra field with a value of 'XMPP'
                // however we don’t have such a field and thus have to use the legacy PROTOCOL_JABBER
            }
            intent.putExtra("finishActivityOnSaveCompleted", true);
            try {
                startActivityForResult(intent, 0);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(ContactDetailsActivity.this, R.string.no_application_found_to_view_contact, Toast.LENGTH_SHORT).show();
            }
        });
        builder.create().show();
    }

    @Override
    public void onRosterUpdate() {
        refreshUi();
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    @Override
    public void OnUpdateBlocklist(final Status status) {
        refreshUi();
    }

    @Override
    protected void refreshUiReal() {
        invalidateOptionsMenu();
        populateView();
    }

    @Override
    protected String getShareableUri(boolean http) {
        if (http) {
            return "https://conversations.im/i/" + XmppUri.lameUrlEncode(contact.getJid().asBareJid().toEscapedString());
        } else {
            return "xmpp:" + contact.getJid().asBareJid().toEscapedString();
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showInactiveOmemo = savedInstanceState != null && savedInstanceState.getBoolean("show_inactive_omemo", false);
        if (getIntent().getAction().equals(ACTION_VIEW_CONTACT)) {
            try {
                this.accountJid = Jid.ofEscaped(getIntent().getExtras().getString(EXTRA_ACCOUNT));
            } catch (final IllegalArgumentException ignored) {
            }
            try {
                this.contactJid = Jid.ofEscaped(getIntent().getExtras().getString("contact"));
            } catch (final IllegalArgumentException ignored) {
            }
        }
        this.messageFingerprint = getIntent().getStringExtra("fingerprint");
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_contact_details);

        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar());
        binding.showInactiveDevices.setOnClickListener(v -> {
            showInactiveOmemo = !showInactiveOmemo;
            populateView();
        });
        binding.addContactButton.setOnClickListener(v -> showAddToRosterDialog(contact));

        mMediaAdapter = new MediaAdapter(this, R.dimen.media_size);
        this.binding.media.setAdapter(mMediaAdapter);
        GridManager.setupLayoutManager(this, this.binding.media, R.dimen.media_size);
        binding.editContactNameButton.setOnClickListener(v->{
            Uri systemAccount = contact.getSystemAccount();
            if (systemAccount == null) {
                quickEdit(contact.getServerName(), R.string.contact_name, value -> {
                    contact.setServerName(value);
                    ContactDetailsActivity.this.xmppConnectionService.pushContactToServer(contact);
                    populateView();
                    return null;
                }, true);
            } else {
                Intent intent = new Intent(Intent.ACTION_EDIT);
                intent.setDataAndType(systemAccount, Contacts.CONTENT_ITEM_TYPE);
                intent.putExtra("finishActivityOnSaveCompleted", true);
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(ContactDetailsActivity.this, R.string.no_application_found_to_view_contact, Toast.LENGTH_SHORT).show();
                }

            }
        });
        MenuBuilder menuBuilder = new MenuBuilder(this);
        MenuInflater inflater = new MenuInflater(this);
        inflater.inflate(R.menu.share_uri, menuBuilder);
        binding.shareContactButton.setOnClickListener(view -> {
        Context wrapper = new ContextThemeWrapper(this, R.style.ThemeOverlay_AppCompat_Light);
        MenuPopupHelper optionsMenu = new MenuPopupHelper(wrapper, menuBuilder,binding.shareUriText);
        optionsMenu.setForceShowIcon(true);
            menuBuilder.setCallback(new MenuBuilder.Callback() {
                @Override
                public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
                    switch (item.getItemId()) {
                        case R.id.action_share_uri:
                            shareLink(true);
                            break;
                        case R.id.action_share_http:
                            shareLink(false);
                            break;
                    }
                    return true;
                }
                @Override
                public void onMenuModeChange(MenuBuilder menu) {}
            });
            optionsMenu.show();
        });
        binding.deleteContactButton.setOnClickListener(v->{
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
//            builder.setTitle(getString(R.string.action_delete_contact))
//                    .setMessage(JidDialog.style(this, R.string.remove_contact_text, contact.getJid().toEscapedString()))
//                    .setPositiveButton(getString(R.string.delete),
//                            removeFromRoster).create().show();

            final DialogDeleteContactBinding binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.dialog_delete_contact, null, false);
            builder.setView(binding.getRoot());
            final AlertDialog dialog = builder.create();
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.show();
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = (int) (348 * this.getResources().getDisplayMetrics().density);
            dialog.getWindow().setAttributes(layoutParams);
            binding.deleteContactMessage.setText(JidDialog.style(this, R.string.remove_contact_text, contact.getJid().toEscapedString()));
            binding.cancelButton.setOnClickListener((v1 ->  dialog.dismiss()));
            binding.deleteButton.setOnClickListener(v1->{
                xmppConnectionService.deleteContactOnServer(contact);
                dialog.dismiss();
            });
        });
       binding.positiveButton.setOnClickListener(v->BlockContactDialog.show(this, contact));
       binding.unblockContactButton.setOnClickListener(v->BlockContactDialog.show(this, contact));
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        savedInstanceState.putBoolean("show_inactive_omemo", showInactiveOmemo);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        } else {
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            this.showDynamicTags = preferences.getBoolean(SettingsActivity.SHOW_DYNAMIC_TAGS, false);
            this.showLastSeen = preferences.getBoolean("last_activity", false);
        }
        binding.mediaWrapperLayout.setVisibility(Compatibility.hasStoragePermission(this) ? View.VISIBLE : View.GONE);
        mMediaAdapter.setAttachments(Collections.emptyList());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0)
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == REQUEST_SYNC_CONTACTS && xmppConnectionServiceBound) {
                    showAddToPhoneBookDialog();
                    xmppConnectionService.loadPhoneContacts();
                    xmppConnectionService.startContactObserver();
                }
            }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton(getString(R.string.cancel), null);
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_share_http:
                shareLink(true);
                break;
            case R.id.action_share_uri:
                shareLink(false);
                break;
            case R.id.action_delete_contact:
                builder.setTitle(getString(R.string.action_delete_contact))
                        .setMessage(JidDialog.style(this, R.string.remove_contact_text, contact.getJid().toEscapedString()))
                        .setPositiveButton(getString(R.string.delete),
                                removeFromRoster).create().show();
                break;
            case R.id.action_edit_contact:
                Uri systemAccount = contact.getSystemAccount();
                if (systemAccount == null) {
                    quickEdit(contact.getServerName(), R.string.contact_name, value -> {
                        contact.setServerName(value);
                        ContactDetailsActivity.this.xmppConnectionService.pushContactToServer(contact);
                        populateView();
                        return null;
                    }, true);
                } else {
                    Intent intent = new Intent(Intent.ACTION_EDIT);
                    intent.setDataAndType(systemAccount, Contacts.CONTENT_ITEM_TYPE);
                    intent.putExtra("finishActivityOnSaveCompleted", true);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(ContactDetailsActivity.this, R.string.no_application_found_to_view_contact, Toast.LENGTH_SHORT).show();
                    }

                }
                break;
            case R.id.action_block:
                BlockContactDialog.show(this, contact);
                break;
            case R.id.action_unblock:
                BlockContactDialog.show(this, contact);
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.contact_details, menu);
//        AccountUtils.showHideMenuItems(menu);
        MenuItem block = menu.findItem(R.id.action_block);
        MenuItem unblock = menu.findItem(R.id.action_unblock);
        MenuItem edit = menu.findItem(R.id.action_edit_contact);
        MenuItem delete = menu.findItem(R.id.action_delete_contact);
        if (contact == null) {
            return true;
        }
        final XmppConnection connection = contact.getAccount().getXmppConnection();
        if (connection != null && connection.getFeatures().blocking()) {
            if (this.contact.isBlocked()) {
                block.setVisible(false);
            } else {
                unblock.setVisible(false);
            }
        } else {
            unblock.setVisible(false);
            block.setVisible(false);
        }
        if (!contact.showInRoster()) {
            edit.setVisible(false);
            delete.setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }

    private void populateView() {
        if (contact == null) {
            return;
        }
        invalidateOptionsMenu();
        setTitle(contact.getDisplayName());
        if (contact.showInRoster()) {
            binding.detailsSendPresence.setVisibility(View.VISIBLE);
            binding.detailsReceivePresence.setVisibility(View.VISIBLE);
//            binding.addContactButton.setVisibility(View.GONE);
            binding.detailsSendPresence.setOnCheckedChangeListener(null);
            binding.detailsReceivePresence.setOnCheckedChangeListener(null);
//            binding.deleteContactButton.setVisibility(View.VISIBLE);
//            binding.editContactNameButton.setVisibility(View.VISIBLE);
            List<String> statusMessages = contact.getPresences().getStatusMessages();
            if (statusMessages.size() == 0) {
                binding.statusMessage.setVisibility(View.GONE);
            } else if (statusMessages.size() == 1) {
                final String message = statusMessages.get(0);
                binding.statusMessage.setVisibility(View.VISIBLE);
                final Spannable span = new SpannableString(message);
                if (Emoticons.isOnlyEmoji(message)) {
                    span.setSpan(new RelativeSizeSpan(2.0f), 0, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                binding.statusMessage.setText(span);
            } else {
                StringBuilder builder = new StringBuilder();
                binding.statusMessage.setVisibility(View.VISIBLE);
                int s = statusMessages.size();
                for (int i = 0; i < s; ++i) {
                    builder.append(statusMessages.get(i));
                    if (i < s - 1) {
                        builder.append("\n");
                    }
                }
                binding.statusMessage.setText(builder);
            }

            if (contact.getOption(Contact.Options.FROM)) {
                binding.detailsSendPresence.setText(R.string.send_presence_updates);
                binding.detailsSendPresence.setChecked(true);
            } else if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                binding.detailsSendPresence.setChecked(false);
                binding.detailsSendPresence.setText(R.string.send_presence_updates);
            } else {
                binding.detailsSendPresence.setText(R.string.preemptively_grant);
                binding.detailsSendPresence.setChecked(contact.getOption(Contact.Options.PREEMPTIVE_GRANT));
            }
            if (contact.getOption(Contact.Options.TO)) {
                binding.detailsReceivePresence.setText(R.string.receive_presence_updates);
                binding.detailsReceivePresence.setChecked(true);
            } else {
                binding.detailsReceivePresence.setText(R.string.ask_for_presence_updates);
                binding.detailsReceivePresence.setChecked(contact.getOption(Contact.Options.ASKING));
            }
            if (contact.getAccount().isOnlineAndConnected()) {
                binding.detailsReceivePresence.setEnabled(true);
                binding.detailsSendPresence.setEnabled(true);
            } else {
                binding.detailsReceivePresence.setEnabled(false);
                binding.detailsSendPresence.setEnabled(false);
            }
            binding.detailsSendPresence.setOnCheckedChangeListener(this.mOnSendCheckedChange);
            binding.detailsReceivePresence.setOnCheckedChangeListener(this.mOnReceiveCheckedChange);
        } else {
//            binding.addContactButton.setVisibility(View.VISIBLE);
            binding.detailsSendPresence.setVisibility(View.GONE);
            binding.detailsReceivePresence.setVisibility(View.GONE);
            binding.statusMessage.setVisibility(View.GONE);
//            binding.deleteContactButton.setVisibility(View.GONE);
//            binding.editContactNameButton.setVisibility(View.GONE);
        }

        if (contact.isBlocked()) {
            binding.detailsLastseen.setVisibility(View.VISIBLE);
            binding.detailsLastseen.setText(R.string.contact_blocked);
        } else {
            if (showLastSeen
                    && contact.getLastseen() > 0
                    && contact.getPresences().allOrNonSupport(Namespace.IDLE)) {
                binding.detailsLastseen.setVisibility(View.VISIBLE);
                binding.detailsLastseen.setText(UIHelper.lastseen(getApplicationContext(), contact.isActive(), contact.getLastseen()));
            } else {
                binding.detailsLastseen.setVisibility(View.GONE);
            }
        }

        binding.detailsContactjid.setText(IrregularUnicodeDetector.style(this, contact.getJid()));
        String account;
        if (Config.DOMAIN_LOCK != null) {
            account = contact.getAccount().getJid().getEscapedLocal();
        } else {
            account = contact.getAccount().getJid().asBareJid().toEscapedString();
        }
        binding.detailsAccount.setText(getString(R.string.using_account, account));
        AvatarWorkerTask.loadAvatar(contact, binding.detailsContactBadge, R.dimen.avatar_on_details_screen_size);
        binding.detailsContactBadge.setOnClickListener(this::onBadgeClick);
        binding.addToDeviceContact.setOnClickListener(this::addToContactClick);

        binding.detailsContactKeys.removeAllViews();
        boolean hasKeys = false;
        final LayoutInflater inflater = getLayoutInflater();
        final AxolotlService axolotlService = contact.getAccount().getAxolotlService();
        if (Config.supportOmemo() && axolotlService != null) {
            final Collection<XmppAxolotlSession> sessions = axolotlService.findSessionsForContact(contact);
            boolean anyActive = false;
            for (XmppAxolotlSession session : sessions) {
                anyActive = session.getTrust().isActive();
                if (anyActive) {
                    break;
                }
            }
            boolean skippedInactive = false;
            boolean showsInactive = false;
            for (final XmppAxolotlSession session : sessions) {
                final FingerprintStatus trust = session.getTrust();
                hasKeys |= !trust.isCompromised();
                if (!trust.isActive() && anyActive) {
                    if (showInactiveOmemo) {
                        showsInactive = true;
                    } else {
                        skippedInactive = true;
                        continue;
                    }
                }
                if (!trust.isCompromised()) {
                    boolean highlight = session.getFingerprint().equals(messageFingerprint);
                    addFingerprintRow(binding.detailsContactKeys, session, highlight);
                }
            }
            if (showsInactive || skippedInactive) {
                binding.showInactiveDevices.setText(showsInactive ? R.string.hide_inactive_devices : R.string.show_inactive_devices);
                binding.showInactiveDevices.setVisibility(View.VISIBLE);
            } else {
                binding.showInactiveDevices.setVisibility(View.GONE);
            }
        } else {
            binding.showInactiveDevices.setVisibility(View.GONE);
        }
        binding.scanButton.setVisibility(hasKeys && isCameraFeatureAvailable() ? View.VISIBLE : View.GONE);
        if (hasKeys) {
            binding.scanButton.setOnClickListener((v) -> ScanActivity.scan(this));
        }
        if (Config.supportOpenPgp() && contact.getPgpKeyId() != 0) {
            hasKeys = true;
            View view = inflater.inflate(R.layout.contact_key, binding.detailsContactKeys, false);
            TextView key = view.findViewById(R.id.key);
            TextView keyType = view.findViewById(R.id.key_type);
            keyType.setText(R.string.openpgp_key_id);
            if ("pgp".equals(messageFingerprint)) {
                keyType.setTextAppearance(this, R.style.TextAppearance_Conversations_Caption_Highlight);
            }
            key.setText(OpenPgpUtils.convertKeyIdToHex(contact.getPgpKeyId()));
            final OnClickListener openKey = v -> launchOpenKeyChain(contact.getPgpKeyId());
            view.setOnClickListener(openKey);
            key.setOnClickListener(openKey);
            keyType.setOnClickListener(openKey);
            binding.detailsContactKeys.addView(view);
        }
//        binding.keysWrapper.setVisibility(hasKeys ? View.VISIBLE : View.GONE);

        List<ListItem.Tag> tagList = contact.getTags(this);
        if (tagList.size() == 0 || !this.showDynamicTags) {
            binding.tags.setVisibility(View.GONE);
        } else {
            binding.tags.setVisibility(View.VISIBLE);
            binding.tags.removeAllViewsInLayout();
            for (final ListItem.Tag tag : tagList) {
                final TextView tv = (TextView) inflater.inflate(R.layout.list_item_tag, binding.tags, false);
                tv.setText(tag.getName());
                tv.setBackgroundColor(tag.getColor());
                binding.tags.addView(tv);
            }
        }
        if(contact.getAccount().isOnlineAndConnected()){
            binding.blockUnblockDeleteCard.setVisibility(View.VISIBLE);
            if(contact.showInRoster()){
                binding.editContactNameButton.setVisibility(View.VISIBLE);
                binding.deleteContactButton.setVisibility(View.VISIBLE);
                binding.addContactButton.setVisibility(View.GONE);
            }else{
                binding.editContactNameButton.setVisibility(View.GONE);
                binding.addContactButton.setVisibility(View.VISIBLE);
                binding.deleteContactButton.setVisibility(View.GONE);
            }
        }else{
            binding.blockUnblockDeleteCard.setVisibility(View.GONE);
            binding.editContactNameButton.setVisibility(View.GONE);
            binding.addContactButton.setVisibility(View.GONE);
        }
        if(contact.isBlocked()){
            binding.positiveButton.setVisibility(View.GONE);
        }else{
            binding.unblockContactButton.setVisibility(View.GONE);
            binding.positiveButton.setVisibility(View.VISIBLE);
        }
//        if(contact.getShownStatus().name().equals("ONLINE")){
//            binding.onlineTag.setVisibility(View.VISIBLE);
//        }
    }
    private void addToContactClick(View view) {
        final Uri systemAccount = contact.getSystemAccount();
        if (systemAccount == null) {
            checkContactPermissionAndShowAddDialog();
        } else {
            final Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(systemAccount);
            try {
                startActivity(intent);
            } catch (final ActivityNotFoundException e) {
                Toast.makeText(this, R.string.no_application_found_to_view_contact, Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void onBadgeClick(View view) {
        final XmppActivity activity = XmppActivity.find(binding.detailsContactBadge);
        if (activity == null) {
            return;
        }
        final Bitmap bm = activity.avatarService().get(contact, (int) activity.getResources().getDimension(R.dimen.avatar_on_details_screen_size), true);
        if (bm == null) {
            return;
        }
        Dialog settingsDialog = new Dialog(this);
        settingsDialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        LayoutInflater inflater = getLayoutInflater();
        View dialogLayout = inflater.inflate(R.layout.image_view_dialog, null);
        ImageView imageView = dialogLayout.findViewById(R.id.image_dialog);
        imageView.setImageBitmap(bm);
        settingsDialog.setContentView(dialogLayout);
        settingsDialog.show();
    }
    public void onBackendConnected() {
        if (accountJid != null && contactJid != null) {
            Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account == null) {
                return;
            }
            this.contact = account.getRoster().getContact(contactJid);
            if (mPendingFingerprintVerificationUri != null) {
                processFingerprintVerification(mPendingFingerprintVerificationUri);
                mPendingFingerprintVerificationUri = null;
            }

            if (Compatibility.hasStoragePermission(this)) {
                final int limit = GridManager.getCurrentColumnCount(this.binding.media);
                xmppConnectionService.getAttachments(account, contact.getJid().asBareJid(), limit, this);
                this.binding.mediaWrapperLayout.setOnClickListener((v) -> MediaBrowserActivity.launch(this, contact));
            }
            populateView();
        }
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }

    @Override
    protected void processFingerprintVerification(XmppUri uri) {
        if (contact != null && contact.getJid().asBareJid().equals(uri.getJid()) && uri.hasFingerprints()) {
            if (xmppConnectionService.verifyFingerprints(contact, uri.getFingerprints())) {
                Toast.makeText(this, R.string.verified_fingerprints, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.invalid_barcode, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMediaLoaded(List<Attachment> attachments) {
        runOnUiThread(() -> {
            int limit = GridManager.getCurrentColumnCount(binding.media);
            mMediaAdapter.setAttachments(attachments.subList(0, Math.min(limit, attachments.size())));
            binding.mediaWrapperLayout.setVisibility(attachments.size() > 0 ? View.VISIBLE : View.GONE);
        });

    }
}