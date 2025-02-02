/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.auva.ui;


import static eu.siacs.auva.ui.ConversationFragment.REQUEST_DECRYPT_PGP;
import static eu.siacs.auva.ui.StartConversationActivity.getSelectedAccount;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.textfield.TextInputLayout;

import org.openintents.openpgp.util.OpenPgpApi;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.auva.Config;
import eu.siacs.auva.R;
import eu.siacs.auva.crypto.OmemoSetting;
import eu.siacs.auva.databinding.ActivityConversationsBinding;
import eu.siacs.auva.entities.Account;
import eu.siacs.auva.entities.Bookmark;
import eu.siacs.auva.entities.Contact;
import eu.siacs.auva.entities.Conversation;
import eu.siacs.auva.entities.Conversational;
import eu.siacs.auva.entities.MucOptions;
import eu.siacs.auva.services.XmppConnectionService;
import eu.siacs.auva.ui.interfaces.OnBackendConnected;
import eu.siacs.auva.ui.interfaces.OnConversationArchived;
import eu.siacs.auva.ui.interfaces.OnConversationRead;
import eu.siacs.auva.ui.interfaces.OnConversationSelected;
import eu.siacs.auva.ui.interfaces.OnConversationsListItemUpdated;
import eu.siacs.auva.ui.util.ActionBarUtil;
import eu.siacs.auva.ui.util.ActivityResult;
import eu.siacs.auva.ui.util.AvatarWorkerTask;
import eu.siacs.auva.ui.util.ConversationMenuConfigurator;
import eu.siacs.auva.ui.util.MenuDoubleTabUtil;
import eu.siacs.auva.ui.util.PendingItem;
import eu.siacs.auva.ui.util.SoftKeyboardUtils;
import eu.siacs.auva.utils.AccountUtils;
import eu.siacs.auva.utils.ExceptionHelper;
import eu.siacs.auva.utils.SignupUtils;
import eu.siacs.auva.utils.XmppUri;
import eu.siacs.auva.xmpp.Jid;
import eu.siacs.auva.xmpp.OnUpdateBlocklist;

public class ConversationsActivity extends XmppActivity implements OnConversationSelected, OnConversationArchived, OnConversationsListItemUpdated, OnConversationRead, XmppConnectionService.OnAccountUpdate, XmppConnectionService.OnConversationUpdate, XmppConnectionService.OnRosterUpdate, OnUpdateBlocklist, XmppConnectionService.OnShowErrorToast, XmppConnectionService.OnAffiliationChanged , JoinConferenceDialog.JoinConferenceDialogListener, CreatePrivateGroupChatDialog.CreateConferenceDialogListener, CreatePublicChannelDialog.CreatePublicChannelDialogListener{

    public static final String ACTION_VIEW_CONVERSATION = "eu.siacs.conversations.action.VIEW";
    public static final String EXTRA_CONVERSATION = "conversationUuid";
    public static final String EXTRA_DOWNLOAD_UUID = "eu.siacs.conversations.download_uuid";
    public static final String EXTRA_AS_QUOTE = "eu.siacs.conversations.as_quote";
    public static final String EXTRA_NICK = "nick";
    public static final String EXTRA_IS_PRIVATE_MESSAGE = "pm";
    public static final String EXTRA_DO_NOT_APPEND = "do_not_append";
    public static final String EXTRA_POST_INIT_ACTION = "post_init_action";
    public static final String POST_ACTION_RECORD_VOICE = "record_voice";
    public static final String EXTRA_TYPE = "type";

    private static final List<String> VIEW_AND_SHARE_ACTIONS = Arrays.asList(
            ACTION_VIEW_CONVERSATION,
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE
    );

    public static final int REQUEST_OPEN_MESSAGE = 0x9876;
    public static final int REQUEST_PLAY_PAUSE = 0x5432;


    //secondary fragment (when holding the conversation, must be initialized before refreshing the overview fragment
    private static final @IdRes
    int[] FRAGMENT_ID_NOTIFICATION_ORDER = {R.id.secondary_fragment, R.id.main_fragment};
    private final PendingItem<Intent> pendingViewIntent = new PendingItem<>();
    private final PendingItem<ActivityResult> postponedActivityResult = new PendingItem<>();
    private ActivityConversationsBinding binding;
    private boolean mActivityPaused = true;
    private final AtomicBoolean mRedirectInProcess = new AtomicBoolean(false);
    private final int REQUEST_CREATE_CONFERENCE = 0x39da;
    private Pair<Integer, Intent> mPostponedActivityResult;
    private final UiCallback<Conversation> mAdhocConferenceCallback = new UiCallback<Conversation>() {
        @Override
        public void success(final Conversation conversation) {
            runOnUiThread(() -> {
                hideToast();
                switchToConversation(conversation);
            });
        }

        @Override
        public void error(final int errorCode, Conversation object) {
            runOnUiThread(() -> replaceToast(getString(errorCode)));
        }

        @Override
        public void userInputRequired(PendingIntent pi, Conversation object) {

        }
    };

    private static boolean isViewOrShareIntent(Intent i) {
        Log.d(Config.LOGTAG, "action: " + (i == null ? null : i.getAction()));
        return i != null && VIEW_AND_SHARE_ACTIONS.contains(i.getAction()) && i.hasExtra(EXTRA_CONVERSATION);
    }

    private static Intent createLauncherIntent(Context context) {
        final Intent intent = new Intent(context, ConversationsActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        return intent;
    }

    @Override
    protected void refreshUiReal() {
        invalidateOptionsMenu();
        for (@IdRes int id : FRAGMENT_ID_NOTIFICATION_ORDER) {
            refreshFragment(id);
        }
    }
    public interface OnBackClickListener {
        boolean onBackClick();
    }

    private OnBackClickListener onBackClickListener;

    public void setOnBackClickListener(OnBackClickListener onBackClickListener) {
        this.onBackClickListener = onBackClickListener;
    }

    @Override
    public void onBackPressed() {
        if (onBackClickListener != null && onBackClickListener.onBackClick()) {
            super.onBackPressed();
        }
    }

    @Override
    void onBackendConnected() {
        if (performRedirectIfNecessary(true)) {
            return;
        }
        xmppConnectionService.getNotificationService().setIsInForeground(true);
        Intent intent = pendingViewIntent.pop();
        if (intent != null) {
            if (processViewIntent(intent)) {
                if (binding.secondaryFragment != null) {
                    notifyFragmentOfBackendConnected(R.id.main_fragment);
                }
                invalidateActionBarTitle();
                return;
            }
        }
        for (@IdRes int id : FRAGMENT_ID_NOTIFICATION_ORDER) {
            notifyFragmentOfBackendConnected(id);
        }

        ActivityResult activityResult = postponedActivityResult.pop();
        if (activityResult != null) {
            handleActivityResult(activityResult);
        }

        invalidateActionBarTitle();
        if (binding.secondaryFragment != null && ConversationFragment.getConversation(this) == null) {
            Conversation conversation = ConversationsOverviewFragment.getSuggestion(this);
            if (conversation != null) {
                openConversation(conversation, null);
            }
        }
        showDialogsIfMainIsOverview();
    }

    private boolean performRedirectIfNecessary(boolean noAnimation) {
        return performRedirectIfNecessary(null, noAnimation);
    }

    private boolean performRedirectIfNecessary(final Conversation ignore, final boolean noAnimation) {
        if (xmppConnectionService == null) {
            return false;
        }
        boolean isConversationsListEmpty = xmppConnectionService.isConversationsListEmpty(ignore);
        if (isConversationsListEmpty && mRedirectInProcess.compareAndSet(false, true)) {
            final Intent intent = SignupUtils.getRedirectionIntent(this);
            if (noAnimation) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            }
            runOnUiThread(() -> {
                startActivity(intent);
                if (noAnimation) {
                    overridePendingTransition(0, 0);
                }
            });
        }
        return mRedirectInProcess.get();
    }

    private void showDialogsIfMainIsOverview() {
        if (xmppConnectionService == null) {
            return;
        }
        final Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationsOverviewFragment) {
            if (ExceptionHelper.checkForCrash(this)) {
                return;
            }
            //openBatteryOptimizationDialogIfNeeded();
        }
    }

    private void createPrivateGroupChat(int requestCode, Intent intent) {
        try {
            this.mPostponedActivityResult = null;
            if (requestCode == REQUEST_CREATE_CONFERENCE) {
                Account account = extractAccount(intent);
                final String name = intent.getStringExtra(ChooseContactActivity.EXTRA_GROUP_CHAT_NAME);
                final List<Jid> jids = ChooseContactActivity.extractJabberIds(intent);
                if (account != null && jids.size() > 0) {
                    if (xmppConnectionService.createAdhocConference(account, name, jids, mAdhocConferenceCallback)) {
                        mToast = Toast.makeText(this, R.string.creating_conference, Toast.LENGTH_LONG);
                        mToast.show();
                    }
                }
            }
        } catch (Exception e) {
            Log.d("error :", "cann't create private group chat");
            this.mPostponedActivityResult = new Pair<>(requestCode, intent);
        }
    }

    private String getBatteryOptimizationPreferenceKey() {
        @SuppressLint("HardwareIds") String device = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        return "show_battery_optimization" + (device == null ? "" : device);
    }

    private void setNeverAskForBatteryOptimizationsAgain() {
        getPreferences().edit().putBoolean(getBatteryOptimizationPreferenceKey(), false).apply();
    }

    @SuppressLint("StringFormatInvalid")
    private void openBatteryOptimizationDialogIfNeeded() {
        if (hasAccountWithoutPush()
                && isOptimizingBattery()
                && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                && getPreferences().getBoolean(getBatteryOptimizationPreferenceKey(), true)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.battery_optimizations_enabled);
            builder.setMessage(getString(R.string.battery_optimizations_enabled_dialog, getString(R.string.app_name)));
            builder.setPositiveButton(R.string.next, (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                Uri uri = Uri.parse("package:" + getPackageName());
                intent.setData(uri);
                try {
                    startActivityForResult(intent, REQUEST_BATTERY_OP);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, R.string.device_does_not_support_battery_op, Toast.LENGTH_SHORT).show();
                }
            });
            builder.setOnDismissListener(dialog -> setNeverAskForBatteryOptimizationsAgain());
            final AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        }
    }

    private boolean hasAccountWithoutPush() {
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() == Account.State.ONLINE && !xmppConnectionService.getPushManagementService().available(account)) {
                return true;
            }
        }
        return false;
    }

    private void notifyFragmentOfBackendConnected(@IdRes int id) {
        final Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment instanceof OnBackendConnected) {
            ((OnBackendConnected) fragment).onBackendConnected();
        }
    }

    private void refreshFragment(@IdRes int id) {
        final Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment instanceof XmppFragment) {
            ((XmppFragment) fragment).refresh();
        }
    }

    private boolean processViewIntent(Intent intent) {
        final String uuid = intent.getStringExtra(EXTRA_CONVERSATION);
        final Conversation conversation = uuid != null ? xmppConnectionService.findConversationByUuid(uuid) : null;
        if (conversation == null) {
            Log.d(Config.LOGTAG, "unable to view conversation with uuid:" + uuid);
            return false;
        }
        openConversation(conversation, intent.getExtras());
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        UriHandlerActivity.onRequestPermissionResult(this, requestCode, grantResults);
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                switch (requestCode) {
                    case REQUEST_OPEN_MESSAGE:
                        refreshUiReal();
                        ConversationFragment.openPendingMessage(this);
                        break;
                    case REQUEST_PLAY_PAUSE:
                        ConversationFragment.startStopPending(this);
                        break;
                }
            }
        }
    }

   @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ActivityResult activityResult = ActivityResult.of(requestCode, resultCode, data);
        if (xmppConnectionService != null) {
            handleActivityResult(activityResult);
        } else {
            this.postponedActivityResult.push(activityResult);
        }
    }

    private void handleActivityResult(ActivityResult activityResult) {
        if (activityResult.resultCode == Activity.RESULT_OK) {
            handlePositiveActivityResult(activityResult.requestCode, activityResult.data);
        } else {
            handleNegativeActivityResult(activityResult.requestCode);
        }
    }

    private void handleNegativeActivityResult(int requestCode) {
        Conversation conversation = ConversationFragment.getConversationReliable(this);
        switch (requestCode) {
            case REQUEST_DECRYPT_PGP:
                if (conversation == null) {
                    break;
                }
                conversation.getAccount().getPgpDecryptionService().giveUpCurrentDecryption();
                break;
            case REQUEST_BATTERY_OP:
                setNeverAskForBatteryOptimizationsAgain();
                break;
        }
    }

    private void handlePositiveActivityResult(int requestCode, final Intent data) {
        Conversation conversation = ConversationFragment.getConversationReliable(this);
        if (requestCode == REQUEST_CREATE_CONFERENCE) {
            createPrivateGroupChat(requestCode, data);
        }
        if (conversation == null) {
            Log.d(Config.LOGTAG, "conversation not found");
            return;
        }
        switch (requestCode) {
            case REQUEST_DECRYPT_PGP:
                conversation.getAccount().getPgpDecryptionService().continueDecryption(data);
                break;
            case REQUEST_CHOOSE_PGP_ID:
                long id = data.getLongExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, 0);
                if (id != 0) {
                    conversation.getAccount().setPgpSignId(id);
                    announcePgp(conversation.getAccount(), null, null, onOpenPGPKeyPublished);
                } else {
                    choosePgpSignId(conversation.getAccount());
                }
                break;
            case REQUEST_ANNOUNCE_PGP:
                announcePgp(conversation.getAccount(), conversation, data, onOpenPGPKeyPublished);
                break;
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConversationMenuConfigurator.reloadFeatures(this);
        OmemoSetting.load(this);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_conversations);
        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar());
        this.getFragmentManager().addOnBackStackChangedListener(this::invalidateActionBarTitle);
        this.getFragmentManager().addOnBackStackChangedListener(this::showDialogsIfMainIsOverview);
        this.initializeFragments();
        this.invalidateActionBarTitle();
        final Intent intent;
        if (savedInstanceState == null) {
            intent = getIntent();
        } else {
            intent = savedInstanceState.getParcelable("intent");
        }
        if (isViewOrShareIntent(intent)) {
            pendingViewIntent.push(intent);
            setIntent(createLauncherIntent(this));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_conversations, menu);
        final MenuItem qrCodeScanMenuItem = menu.findItem(R.id.action_scan_qr_code);
        if (qrCodeScanMenuItem != null) {
            if (isCameraFeatureAvailable()) {
                Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
                boolean visible = getResources().getBoolean(R.bool.show_qr_code_scan)
                        && fragment instanceof ConversationsOverviewFragment;
                qrCodeScanMenuItem.setVisible(visible);
            } else {
                qrCodeScanMenuItem.setVisible(false);
            }
        }
        qrCodeScanMenuItem.setVisible(false);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onConversationSelected(Conversation conversation) {
        clearPendingViewIntent();
        if (ConversationFragment.getConversation(this) == conversation) {
            Log.d(Config.LOGTAG, "ignore onConversationSelected() because conversation is already open");
            return;
        }
        openConversation(conversation, null);
    }

    public void clearPendingViewIntent() {
        if (pendingViewIntent.clear()) {
            Log.e(Config.LOGTAG, "cleared pending view intent");
        }
    }

    private void displayToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(ConversationsActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onAffiliationChangedSuccessful(Jid jid) {

    }

    @Override
    public void onAffiliationChangeFailed(Jid jid, int resId) {
        displayToast(getString(resId, jid.asBareJid().toString()));
    }

    private void openConversation(Conversation conversation, Bundle extras) {
        final FragmentManager fragmentManager = getFragmentManager();
        executePendingTransactions(fragmentManager);
        ConversationFragment conversationFragment = (ConversationFragment) fragmentManager.findFragmentById(R.id.secondary_fragment);
        final boolean mainNeedsRefresh;
        if (conversationFragment == null) {
            mainNeedsRefresh = false;
            final Fragment mainFragment = fragmentManager.findFragmentById(R.id.main_fragment);
            if (mainFragment instanceof ConversationFragment) {
                conversationFragment = (ConversationFragment) mainFragment;
            } else {
                conversationFragment = new ConversationFragment();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.main_fragment, conversationFragment);
                fragmentTransaction.addToBackStack(null);
                try {
                    fragmentTransaction.commit();
                } catch (IllegalStateException e) {
                    Log.w(Config.LOGTAG, "sate loss while opening conversation", e);
                    //allowing state loss is probably fine since view intents et all are already stored and a click can probably be 'ignored'
                    return;
                }
            }
        } else {
            mainNeedsRefresh = true;
        }
        conversationFragment.reInit(conversation, extras == null ? new Bundle() : extras);
        if (mainNeedsRefresh) {
            refreshFragment(R.id.main_fragment);
        } else {
            invalidateActionBarTitle();
        }
    }

    private static void executePendingTransactions(final FragmentManager fragmentManager) {
        try {
            fragmentManager.executePendingTransactions();
        } catch (final Exception e) {
            Log.e(Config.LOGTAG,"unable to execute pending fragment transactions");
        }
    }

    public boolean onXmppUriClicked(Uri uri) {
        XmppUri xmppUri = new XmppUri(uri);
        if (xmppUri.isValidJid() && !xmppUri.hasFingerprints()) {
            final Conversation conversation = xmppConnectionService.findUniqueConversationByJid(xmppUri);
            if (conversation != null) {
                openConversation(conversation, null);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        switch (item.getItemId()) {
            case android.R.id.home:
                FragmentManager fm = getFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    try {
                        fm.popBackStack();
                    } catch (IllegalStateException e) {
                        Log.w(Config.LOGTAG, "Unable to pop back stack after pressing home button");
                    }
                    return true;
                }
                break;
            case R.id.action_scan_qr_code:
                UriHandlerActivity.scan(this);
                return true;
            case R.id.action_search_all_conversations:
                startActivity(new Intent(this, SearchActivity.class));
                return true;
            case R.id.action_search_this_conversation:
                final Conversation conversation = ConversationFragment.getConversation(this);
                if (conversation == null) {
                    return true;
                }
                final Intent intent = new Intent(this, SearchActivity.class);
                intent.putExtra(SearchActivity.EXTRA_CONVERSATION_UUID, conversation.getUuid());
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent keyEvent) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && keyEvent.isCtrlPressed()) {
            final ConversationFragment conversationFragment = ConversationFragment.get(this);
            if (conversationFragment != null && conversationFragment.onArrowUpCtrlPressed()) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, keyEvent);
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        final Intent pendingIntent = pendingViewIntent.peek();
        savedInstanceState.putParcelable("intent", pendingIntent != null ? pendingIntent : getIntent());
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            this.mSkipBackgroundBinding = true;
            recreate();
        } else {
            this.mSkipBackgroundBinding = false;
        }
        mRedirectInProcess.set(false);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if (isViewOrShareIntent(intent)) {
            if (xmppConnectionService != null) {
                clearPendingViewIntent();
                processViewIntent(intent);
            } else {
                pendingViewIntent.push(intent);
            }
        }
        setIntent(createLauncherIntent(this));
    }

    @Override
    public void onPause() {
        this.mActivityPaused = true;
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        this.mActivityPaused = false;
    }

    private void initializeFragments() {
        final FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        final Fragment mainFragment = fragmentManager.findFragmentById(R.id.main_fragment);
        final Fragment secondaryFragment = fragmentManager.findFragmentById(R.id.secondary_fragment);
        if (mainFragment != null) {
            if (binding.secondaryFragment != null) {
                if (mainFragment instanceof ConversationFragment) {
                    getFragmentManager().popBackStack();
                    transaction.remove(mainFragment);
                    transaction.commit();
                    fragmentManager.executePendingTransactions();
                    transaction = fragmentManager.beginTransaction();
                    transaction.replace(R.id.secondary_fragment, mainFragment);
                    transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
                    transaction.commit();
                    return;
                }
            } else {
                if (secondaryFragment instanceof ConversationFragment) {
                    transaction.remove(secondaryFragment);
                    transaction.commit();
                    getFragmentManager().executePendingTransactions();
                    transaction = fragmentManager.beginTransaction();
                    transaction.replace(R.id.main_fragment, secondaryFragment);
                    transaction.addToBackStack(null);
                    transaction.commit();
                    return;
                }
            }
        } else {
            transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
        }
        if (binding.secondaryFragment != null && secondaryFragment == null) {
            transaction.replace(R.id.secondary_fragment, new ConversationFragment());
        }
        transaction.commit();
    }

    private void invalidateActionBarTitle() {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        actionBar.setTitle("");
        final LinearLayout contactInfo = findViewById(R.id.contact_info);
        contactInfo.setVisibility(View.VISIBLE);
        final ImageView profilePhoto = findViewById(R.id.profile_photo);
        final TextView contactName = findViewById(R.id.contact_name);
        final FragmentManager fragmentManager = getFragmentManager();
        final Fragment mainFragment = fragmentManager.findFragmentById(R.id.main_fragment);
        if (mainFragment instanceof ConversationFragment) {
            final Conversation conversation = ((ConversationFragment) mainFragment).getConversation();
            if (conversation != null) {
//               actionBar.setTitle(EmojiWrapper.transform(conversation.getName()));
                AvatarWorkerTask.loadAvatar(conversation.getContact(), profilePhoto, R.dimen.media_size);
                contactName.setVisibility(View.VISIBLE);
                contactName.setText(conversation.getName());
                contactInfo.setOnClickListener(view -> openConversationDetails(conversation));
//                profilePhoto.setOnClickListener(view -> openConversationDetails(conversation));
                actionBar.setDisplayHomeAsUpEnabled(true);
//                ActionBarUtil.setActionBarOnClickListener(
//                        binding.toolbar,
//                        (v) -> openConversationDetails(conversation)
//                );
                return;
            }
        }
//        actionBar.setTitle("");
        if(xmppConnectionService != null){
//            contactName.setVisibility(View.GONE);
            contactName.setVisibility(View.VISIBLE);
            final Account account = AccountUtils.getFirst(xmppConnectionService);
            contactName.setText(account.getJid().getLocal());
            AvatarWorkerTask.loadAvatar(account, profilePhoto, R.dimen.media_size);
        }
        contactInfo.setOnClickListener(view ->AccountUtils.launchManageAccount(this));
//        profilePhoto.setOnClickListener(view -> {
//            AccountUtils.launchManageAccount(this);
//        });
        actionBar.setDisplayHomeAsUpEnabled(false);
        ActionBarUtil.resetActionBarOnClickListeners(binding.toolbar);
    }

    private void openConversationDetails(final Conversation conversation) {
        if (conversation.getMode() == Conversational.MODE_MULTI) {
            ConferenceDetailsActivity.open(this, conversation);
        } else {
            final Contact contact = conversation.getContact();
            if (contact.isSelf()) {
                switchToAccount(conversation.getAccount());
            } else {
                switchToContactDetails(contact);
            }
        }
    }

    @Override
    public void onConversationArchived(Conversation conversation) {
        if (performRedirectIfNecessary(conversation, false)) {
            return;
        }
        final FragmentManager fragmentManager = getFragmentManager();
        final Fragment mainFragment = fragmentManager.findFragmentById(R.id.main_fragment);
        if (mainFragment instanceof ConversationFragment) {
            try {
                fragmentManager.popBackStack();
            } catch (final IllegalStateException e) {
                Log.w(Config.LOGTAG, "state loss while popping back state after archiving conversation", e);
                //this usually means activity is no longer active; meaning on the next open we will run through this again
            }
            return;
        }
        final Fragment secondaryFragment = fragmentManager.findFragmentById(R.id.secondary_fragment);
        if (secondaryFragment instanceof ConversationFragment) {
            if (((ConversationFragment) secondaryFragment).getConversation() == conversation) {
                Conversation suggestion = ConversationsOverviewFragment.getSuggestion(this, conversation);
                if (suggestion != null) {
                    openConversation(suggestion, null);
                }
            }
        }
    }

    @Override
    public void onConversationsListItemUpdated() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationsOverviewFragment) {
            ((ConversationsOverviewFragment) fragment).refresh();
        }
    }

    @Override
    public void switchToConversation(Conversation conversation) {
        Log.d(Config.LOGTAG, "override");
        openConversation(conversation, null);
    }

    @Override
    public void onConversationRead(Conversation conversation, String upToUuid) {
        if (!mActivityPaused && pendingViewIntent.peek() == null) {
            xmppConnectionService.sendReadMarker(conversation, upToUuid);
        } else {
            Log.d(Config.LOGTAG, "ignoring read callback. mActivityPaused=" + mActivityPaused);
        }
    }

    @Override
    public void onAccountUpdate() {
        this.refreshUi();
    }

    @Override
    public void onConversationUpdate() {
        if (performRedirectIfNecessary(false)) {
            return;
        }
        this.refreshUi();
    }

    @Override
    public void onRosterUpdate() {
        this.refreshUi();
    }

    @Override
    public void OnUpdateBlocklist(OnUpdateBlocklist.Status status) {
        this.refreshUi();
    }

    @Override
    public void onShowErrorToast(int resId) {
        runOnUiThread(() -> Toast.makeText(this, resId, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onJoinDialogPositiveClick(Dialog dialog, Spinner spinner, TextInputLayout jidLayout, AutoCompleteTextView jid, boolean isBookmarkChecked) {
        if (!xmppConnectionServiceBound) {
            return;
        }
        final Account account = getSelectedAccount(this, spinner);
        if (account == null) {
            return;
        }
        final String input = jid.getText().toString();
        Jid conferenceJid;
        try {
            conferenceJid = Jid.ofEscaped(input);
        } catch (final IllegalArgumentException e) {
            final XmppUri xmppUri = new XmppUri(input);
            if (xmppUri.isValidJid() && xmppUri.isAction(XmppUri.ACTION_JOIN)) {
                final Editable editable = jid.getEditableText();
                editable.clear();
                editable.append(xmppUri.getJid().toEscapedString());
                conferenceJid = xmppUri.getJid();
            } else {
                jidLayout.setError(getString(R.string.invalid_jid));
                return;
            }
        }

        if (isBookmarkChecked) {
            Bookmark bookmark = account.getBookmark(conferenceJid);
            if (bookmark != null) {
                dialog.dismiss();
                openConversationsForBookmark(bookmark);
            } else {
                bookmark = new Bookmark(account, conferenceJid.asBareJid());
                bookmark.setAutojoin(getBooleanPreference("autojoin", R.bool.autojoin));
                final String nick = conferenceJid.getResource();
                if (nick != null && !nick.isEmpty() && !nick.equals(MucOptions.defaultNick(account))) {
                    bookmark.setNick(nick);
                }
                xmppConnectionService.createBookmark(account, bookmark);
                final Conversation conversation = xmppConnectionService
                        .findOrCreateConversation(account, conferenceJid, true, true, true);
                bookmark.setConversation(conversation);
                dialog.dismiss();
                switchToConversation(conversation);
            }
        } else {
            final Conversation conversation = xmppConnectionService
                    .findOrCreateConversation(account, conferenceJid, true, true, true);
            dialog.dismiss();
            switchToConversation(conversation);
        }
    }
    protected void openConversationsForBookmark(Bookmark bookmark) {
        final Jid jid = bookmark.getFullJid();
        if (jid == null) {
            Toast.makeText(this, R.string.invalid_jid, Toast.LENGTH_SHORT).show();
            return;
        }
        Conversation conversation = xmppConnectionService.findOrCreateConversation(bookmark.getAccount(), jid, true, true, true);
        bookmark.setConversation(conversation);
        if (!bookmark.autojoin() && getPreferences().getBoolean("autojoin", getResources().getBoolean(R.bool.autojoin))) {
            bookmark.setAutojoin(true);
            xmppConnectionService.createBookmark(bookmark.getAccount(), bookmark);
        }
        SoftKeyboardUtils.hideSoftKeyboard(this);
        switchToConversation(conversation);
    }

    @Override
    public void onCreateDialogPositiveClick(Spinner spinner, String name) {
        if (!xmppConnectionServiceBound) {
            return;
        }
        final Account account = getSelectedAccount(this, spinner);
        if (account == null) {
            return;
        }
        Intent intent = new Intent(getApplicationContext(), ChooseContactActivity.class);
        intent.putExtra(ChooseContactActivity.EXTRA_SHOW_ENTER_JID, false);
        intent.putExtra(ChooseContactActivity.EXTRA_SELECT_MULTIPLE, true);
        intent.putExtra(ChooseContactActivity.EXTRA_GROUP_CHAT_NAME, name.trim());
        intent.putExtra(ChooseContactActivity.EXTRA_ACCOUNT, account.getJid().asBareJid().toEscapedString());
        intent.putExtra(ChooseContactActivity.EXTRA_TITLE_RES_ID, R.string.choose_participants);
        startActivityForResult(intent, REQUEST_CREATE_CONFERENCE);
    }
/*
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_OK) {
            if (xmppConnectionServiceBound) {
                this.mPostponedActivityResult = null;
                if (requestCode == REQUEST_CREATE_CONFERENCE) {
                    Account account = extractAccount(intent);
                    final String name = intent.getStringExtra(ChooseContactActivity.EXTRA_GROUP_CHAT_NAME);
                    final List<Jid> jids = ChooseContactActivity.extractJabberIds(intent);
                    if (account != null && jids.size() > 0) {
                        if (xmppConnectionService.createAdhocConference(account, name, jids, mAdhocConferenceCallback)) {
                            mToast = Toast.makeText(this, R.string.creating_conference, Toast.LENGTH_LONG);
                            mToast.show();
                        }
                    }
                }
            } else {
                this.mPostponedActivityResult = new Pair<>(requestCode, intent);
            }
        }
        super.onActivityResult(requestCode, requestCode, intent);
    }
*/
    @Override
    public void onCreatePublicChannel(Account account, String name, Jid address) {
        mToast = Toast.makeText(this, R.string.creating_channel, Toast.LENGTH_LONG);
        mToast.show();
        xmppConnectionService.createPublicChannel(account, name, address, new UiCallback<Conversation>() {
            @Override
            public void success(Conversation conversation) {
                runOnUiThread(() -> {
                    hideToast();
                    switchToConversation(conversation);
                });

            }

            @Override
            public void error(int errorCode, Conversation conversation) {
                runOnUiThread(() -> {
                    replaceToast(getString(errorCode));
                    switchToConversation(conversation);
                });
            }

            @Override
            public void userInputRequired(PendingIntent pi, Conversation object) {

            }
        });
    }
    public void x_button_click(View view) {
        ImageView xButton = findViewById(R.id.x_button);
        findViewById(R.id.reply_Container).setVisibility(View.GONE);
        //xButton.setOnClickListener(v -> findViewById(R.id.reply_Container).setVisibility(View.GONE));
    }
}
