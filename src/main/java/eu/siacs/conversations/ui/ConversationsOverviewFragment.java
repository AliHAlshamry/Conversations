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

package eu.siacs.conversations.ui;

import static androidx.recyclerview.widget.ItemTouchHelper.LEFT;
import static androidx.recyclerview.widget.ItemTouchHelper.RIGHT;
import static eu.siacs.conversations.ui.XmppActivity.FRAGMENT_TAG_DIALOG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.MenuRes;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.collect.Collections2;
import com.leinardi.android.speeddial.SpeedDialActionItem;
import com.leinardi.android.speeddial.SpeedDialView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.FragmentConversationsOverviewBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.ui.adapter.ContactsHorizontalAdapter;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.ui.interfaces.OnConversationArchived;
import eu.siacs.conversations.ui.interfaces.OnConversationSelected;
import eu.siacs.conversations.ui.util.MenuDoubleTabUtil;
import eu.siacs.conversations.ui.util.PendingActionHelper;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.ScrollState;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.EasyOnboardingInvite;
import eu.siacs.conversations.utils.ThemeHelper;
import eu.siacs.conversations.utils.TimeFrameUtils;
import eu.siacs.conversations.utils.XmppUri;

public class ConversationsOverviewFragment extends XmppFragment {

	private static final String STATE_SCROLL_POSITION = ConversationsOverviewFragment.class.getName()+".scroll_state";
	private final List<String> mActivatedAccounts = new ArrayList<>();
	private final List<Contact> contacts = new ArrayList<>();
	private final List<Conversation> conversations = new ArrayList<>();
	private final PendingItem<Conversation> swipedConversation = new PendingItem<>();
	private final PendingItem<ScrollState> pendingScrollState = new PendingItem<>();
	private FragmentConversationsOverviewBinding binding;
	private ContactsHorizontalAdapter conversationHorizontalAdapter;
	private ConversationAdapter conversationAdapter;
	private XmppActivity activity;
	private float mSwipeEscapeVelocity = 0f;
	private final PendingActionHelper pendingActionHelper = new PendingActionHelper();
	private final MenuItem.OnActionExpandListener mOnActionExpandListener = new MenuItem.OnActionExpandListener() {
		@Override
		public boolean onMenuItemActionExpand(MenuItem item) {
			if (binding.speedDial.isOpen()) {
				binding.speedDial.close();
			}
			return true;
		}

		@Override
		public boolean onMenuItemActionCollapse(MenuItem menuItem) {
			return false;
		}
	};
	private final ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0,LEFT|RIGHT) {
		@Override
		public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
			//todo maybe we can manually changing the position of the conversation
			return false;
		}

		@Override
		public float getSwipeEscapeVelocity (float defaultValue) {
			return mSwipeEscapeVelocity;
		}

		@Override
		public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
									float dX, float dY, int actionState, boolean isCurrentlyActive) {
			super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
			if(actionState != ItemTouchHelper.ACTION_STATE_IDLE){
				Paint paint = new Paint();
				paint.setColor(StyledAttributes.getColor(activity,R.attr.conversations_overview_background));
				paint.setStyle(Paint.Style.FILL);
				c.drawRect(viewHolder.itemView.getLeft(),viewHolder.itemView.getTop()
						,viewHolder.itemView.getRight(),viewHolder.itemView.getBottom(), paint);
			}
		}

		@Override
		public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
			super.clearView(recyclerView, viewHolder);
			viewHolder.itemView.setAlpha(1f);
		}

		@Override
		public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
			pendingActionHelper.execute();
			int position = viewHolder.getLayoutPosition();
			try {
				swipedConversation.push(conversations.get(position));
			} catch (IndexOutOfBoundsException e) {
				return;
			}
			conversationAdapter.remove(swipedConversation.peek(), position);
			activity.xmppConnectionService.markRead(swipedConversation.peek());

			if (position == 0 && conversationHorizontalAdapter.getItemCount() == 0) {
				final Conversation c = swipedConversation.pop();
				activity.xmppConnectionService.archiveConversation(c);
				return;
			}
			final boolean formerlySelected = ConversationFragment.getConversation(getActivity()) == swipedConversation.peek();
			if (activity instanceof OnConversationArchived) {
				((OnConversationArchived) activity).onConversationArchived(swipedConversation.peek());
			}
			final Conversation c = swipedConversation.peek();
			final int title;
			if (c.getMode() == Conversational.MODE_MULTI) {
				if (c.getMucOptions().isPrivateAndNonAnonymous()) {
					title = R.string.title_undo_swipe_out_group_chat;
				} else {
					title = R.string.title_undo_swipe_out_channel;
				}
			} else {
				title = R.string.title_undo_swipe_out_conversation;
			}

			final Snackbar snackbar = Snackbar.make(binding.list, title, 5000)
					.setAction(R.string.undo, v -> {
						pendingActionHelper.undo();
						Conversation conversation = swipedConversation.pop();
						conversationAdapter.insert(conversation, position);
						if (formerlySelected) {
							if (activity instanceof OnConversationSelected) {
								((OnConversationSelected) activity).onConversationSelected(c);
							}
						}
						LinearLayoutManager layoutManager = (LinearLayoutManager) binding.list.getLayoutManager();
						if (position > layoutManager.findLastVisibleItemPosition()) {
							binding.list.smoothScrollToPosition(position);
						}
					})
					.addCallback(new Snackbar.Callback() {
						@Override
						public void onDismissed(Snackbar transientBottomBar, int event) {
							switch (event) {
								case DISMISS_EVENT_SWIPE:
								case DISMISS_EVENT_TIMEOUT:
									pendingActionHelper.execute();
									break;
							}
						}
					});

			pendingActionHelper.push(() -> {
				if (snackbar.isShownOrQueued()) {
					snackbar.dismiss();
				}
				final Conversation conversation = swipedConversation.pop();
				if(conversation != null){
					if (!conversation.isRead() && conversation.getMode() == Conversation.MODE_SINGLE) {
						return;
					}
					activity.xmppConnectionService.archiveConversation(c);
				}
			});

			ThemeHelper.fix(snackbar);
			snackbar.show();
		}
	};

	private ItemTouchHelper touchHelper;

	public static Conversation getSuggestion(Activity activity) {
		final Conversation exception;
		Fragment fragment = activity.getFragmentManager().findFragmentById(R.id.main_fragment);
		if (fragment instanceof ConversationsOverviewFragment) {
			exception = ((ConversationsOverviewFragment) fragment).swipedConversation.peek();
		} else {
			exception = null;
		}
		return getSuggestion(activity, exception);
	}

	public static Conversation getSuggestion(Activity activity, Conversation exception) {
		Fragment fragment = activity.getFragmentManager().findFragmentById(R.id.main_fragment);
		if (fragment instanceof ConversationsOverviewFragment) {
			List<Conversation> conversations = ((ConversationsOverviewFragment) fragment).conversations;
			if (conversations.size() > 0) {
				Conversation suggestion = conversations.get(0);
				if (suggestion == exception) {
					if (conversations.size() > 1) {
						return conversations.get(1);
					}
				} else {
					return suggestion;
				}
			}
		}
		return null;

	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		if (savedInstanceState == null) {
			return;
		}
		pendingScrollState.push(savedInstanceState.getParcelable(STATE_SCROLL_POSITION));
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		if (activity instanceof XmppActivity) {
			this.activity = (XmppActivity) activity;
		} else {
			throw new IllegalStateException("Trying to attach fragment to activity that is not an XmppActivity");
		}
	}
	@Override
	public void onDestroyView() {
		Log.d(Config.LOGTAG,"ConversationsOverviewFragment.onDestroyView()");
		super.onDestroyView();
		this.binding = null;
		this.conversationHorizontalAdapter = null;
		this.conversationAdapter = null;
		this.touchHelper = null;
	}
	@Override
	public void onDestroy() {
		Log.d(Config.LOGTAG,"ConversationsOverviewFragment.onDestroy()");
		super.onDestroy();

	}
	@Override
	public void onPause() {
		Log.d(Config.LOGTAG,"ConversationsOverviewFragment.onPause()");
		pendingActionHelper.execute();
		super.onPause();
	}

	@Override
	public void onDetach() {
		super.onDetach();
		this.activity = null;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@SuppressLint("InflateParams")
	protected void showCreateContactDialog(final StartConversationActivity.Invite invite) {
		this.mActivatedAccounts.clear();
		this.mActivatedAccounts.addAll(AccountUtils.getEnabledAccounts(activity.xmppConnectionService));
		FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
		androidx.fragment.app.Fragment prev = activity.getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DIALOG);
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);
		EnterJidDialog dialog = EnterJidDialog.newInstance(
				mActivatedAccounts,
				getString(R.string.add_contact),
				getString(R.string.add),
				null,
				null,
				false,
				true
		);

		dialog.setOnEnterJidDialogPositiveListener((accountJid, contactJid) -> {
			if (!activity.xmppConnectionServiceBound) {
				return false;
			}

			final Account account = activity.xmppConnectionService.findAccountByJid(accountJid);
			if (account == null) {
				return true;
			}

			final Contact contact = account.getRoster().getContact(contactJid);
			if (invite != null && invite.getName() != null) {
				contact.setServerName(invite.getName());
			}
			if (contact.isSelf()) {
				switchToConversation(contact);
				return true;
			} else if (contact.showInRoster()) {
				throw new EnterJidDialog.JidError(getString(R.string.contact_already_exists));
			} else {
				final String preAuth = invite == null ? null : invite.getParameter(XmppUri.PARAMETER_PRE_AUTH);
				activity.xmppConnectionService.createContact(contact, true, preAuth);
				if (invite != null && invite.hasFingerprints()) {
					activity.xmppConnectionService.verifyFingerprints(contact, invite.getFingerprints());
				}
				switchToConversationDoNotAppend(contact, invite == null ? null : invite.getBody());
				return true;
			}
		});
		dialog.show(ft, FRAGMENT_TAG_DIALOG);
	}

	@SuppressLint("InflateParams")
	protected void showJoinConferenceDialog() {
		this.mActivatedAccounts.clear();
		this.mActivatedAccounts.addAll(AccountUtils.getEnabledAccounts(activity.xmppConnectionService));
		FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
		ft.addToBackStack(null);
		JoinConferenceDialog joinConferenceFragment = JoinConferenceDialog.newInstance("", mActivatedAccounts);
		joinConferenceFragment.show(ft, FRAGMENT_TAG_DIALOG);
	}

	private void showPublicChannelDialog() {
		this.mActivatedAccounts.clear();
		this.mActivatedAccounts.addAll(AccountUtils.getEnabledAccounts(activity.xmppConnectionService));
		FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
		androidx.fragment.app.Fragment prev = activity.getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DIALOG);
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);
		CreatePublicChannelDialog dialog = CreatePublicChannelDialog.newInstance(mActivatedAccounts);
		dialog.show(ft, FRAGMENT_TAG_DIALOG);
	}

	private void showCreatePrivateGroupChatDialog() {
		this.mActivatedAccounts.clear();
		this.mActivatedAccounts.addAll(AccountUtils.getEnabledAccounts(activity.xmppConnectionService));
		FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
		androidx.fragment.app.Fragment prev = activity.getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DIALOG);
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);
		CreatePrivateGroupChatDialog createConferenceFragment = CreatePrivateGroupChatDialog.newInstance(mActivatedAccounts);
		createConferenceFragment.show(ft, FRAGMENT_TAG_DIALOG);
	}
	@Override
	public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.mSwipeEscapeVelocity = getResources().getDimension(R.dimen.swipe_escape_velocity);
		this.binding = DataBindingUtil.inflate(inflater, R.layout.fragment_conversations_overview, container, false);
		//this.binding.fab.setOnClickListener((view) -> StartConversationActivity.launch(getActivity()));
		inflateFab(binding.speedDial, R.menu.start_conversation_fab_submenu);
		this.conversationHorizontalAdapter = new ContactsHorizontalAdapter(this.contacts);
		this.conversationAdapter = new ConversationAdapter(this.activity, this.conversations);
		this.conversationHorizontalAdapter.setContactsClickListener((view, contact) -> {
				Conversation startConversation = activity.xmppConnectionService.findOrCreateConversation(contact.getAccount(), contact.getJid(), false, true);
				switchToConversation(startConversation);
				Log.w(ConversationsOverviewFragment.class.getCanonicalName(), "Activity does not implement OnConversationSelected");
		});

		this.conversationAdapter.setConversationClickListener((view, conversation) -> {
			if (activity instanceof OnConversationSelected) {
				((OnConversationSelected) activity).onConversationSelected(conversation);
			} else {
				Log.w(ConversationsOverviewFragment.class.getCanonicalName(), "Activity does not implement OnConversationSelected");
			}
		});
		this.binding.horizontalContactsList.setAdapter(this.conversationHorizontalAdapter);
		this.binding.horizontalContactsList.setLayoutManager(new LinearLayoutManager(getActivity(),LinearLayoutManager.HORIZONTAL,false));
		this.binding.list.setAdapter(this.conversationAdapter);
		this.binding.list.setLayoutManager(new LinearLayoutManager(getActivity(),LinearLayoutManager.VERTICAL,false));

		this.binding.seeAllBtn.setOnClickListener((view) -> StartConversationActivity.launch(getActivity()));
		this.touchHelper = new ItemTouchHelper(this.callback);
		this.touchHelper.attachToRecyclerView(this.binding.horizontalContactsList);
		this.touchHelper.attachToRecyclerView(this.binding.list);
		binding.speedDial.setOnActionSelectedListener(actionItem -> {
			switch (actionItem.getId()) {
				case R.id.discover_public_channels:
					startActivity(new Intent(this.activity, ChannelDiscoveryActivity.class));
					break;
				case R.id.join_public_channel:
					showJoinConferenceDialog();
					break;
				case R.id.create_private_group_chat:
					showCreatePrivateGroupChatDialog();
					break;
				case R.id.create_public_channel:
					showPublicChannelDialog();
					break;
				case R.id.create_contact:
					showCreateContactDialog(null);
					break;
			}
			return false;
		});
		((ConversationsActivity) getActivity()).setOnBackClickListener(new ConversationsActivity.OnBackClickListener() {
			@Override
			public boolean onBackClick() {
				if(binding.speedDial.isOpen()){
					binding.speedDial.close();
					return false;
				}
				return true;
			}
		});
		this.conversationAdapter.setConversationLongClickListener((view, conversation) -> {
			android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(view.getContext(), view.findViewById(R.id.conversation_name));
			getActivity().getMenuInflater().inflate(R.menu.fragment_conversation, popupMenu.getMenu());
			Menu menu = popupMenu.getMenu();
			final MenuItem subMenu = menu.findItem(R.id.sub_menu);
			subMenu.setVisible(false);
			final MenuItem menuMute = menu.findItem(R.id.action_mute1);
			final MenuItem menuUnmute = menu.findItem(R.id.action_unmute1);
			final MenuItem menuClearHistory = menu.findItem(R.id.action_clear_history1);
			menuMute.setVisible(true);
			menuUnmute.setVisible(true);
			menuClearHistory.setVisible(true);
			if (conversation.isMuted()) {
				menuMute.setVisible(false);
			} else {
				menuUnmute.setVisible(false);
			}
			final MenuItem menuTogglePinned = menu.findItem(R.id.action_toggle_pinned1);
			menuTogglePinned.setVisible(true);
			if (conversation.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, false)) {
				menuTogglePinned.setTitle(R.string.remove_from_favorites);
			} else {
				menuTogglePinned.setTitle(R.string.add_to_favorites);
			}
			popupMenu.setOnMenuItemClickListener(menuItem -> {
				switch (menuItem.getItemId()) {
					case R.id.action_clear_history1:
						clearHistoryDialog(conversation);
						break;
					case R.id.action_toggle_pinned1:
						togglePinned(conversation);
						break;
					case R.id.action_mute1:
						muteConversationDialog(conversation);
						break;
					case R.id.action_unmute1:
						unmuteConversation(conversation);
						break;
					default:
						break;
				}
				return true;
			});
			hiddenMenuItem(menu);
			popupMenu.show();
		});
		return binding.getRoot();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
		menuInflater.inflate(R.menu.fragment_conversations_overview, menu);
		AccountUtils.showHideMenuItems(menu);
		final MenuItem easyOnboardInvite = menu.findItem(R.id.action_easy_invite);
		easyOnboardInvite.setVisible(EasyOnboardingInvite.anyHasSupport(activity == null ? null : activity.xmppConnectionService));
	}

	private void inflateFab(final SpeedDialView speedDialView, final @MenuRes int menuRes) {
		speedDialView.clearActionItems();
		final PopupMenu popupMenu = new PopupMenu(this.activity, new View(this.activity));
		popupMenu.inflate(menuRes);
		final Menu menu = popupMenu.getMenu();
		for (int i = 0; i < menu.size(); i++) {
			final MenuItem menuItem = menu.getItem(i);
			if(menuItem.getItemId() != R.id.discover_public_channels){
				final SpeedDialActionItem actionItem = new SpeedDialActionItem.Builder(menuItem.getItemId(), menuItem.getIcon())
						.setLabel(menuItem.getTitle() != null ? menuItem.getTitle().toString() : null)
						.setFabImageTintColor(ContextCompat.getColor(this.activity, R.color.white))
						.create();
				speedDialView.addActionItem(actionItem);}
		}
	}

	@Override
	public void onBackendConnected() {
		refresh();
	}

	@Override
	public void onSaveInstanceState(Bundle bundle) {
		super.onSaveInstanceState(bundle);
		ScrollState scrollState = getScrollState();
		if (scrollState != null) {
			bundle.putParcelable(STATE_SCROLL_POSITION, scrollState);
		}
	}

	private ScrollState getScrollState() {
		if (this.binding == null) {
			return null;
		}
		LinearLayoutManager layoutManager = (LinearLayoutManager) this.binding.list.getLayoutManager();
		int position = layoutManager.findFirstVisibleItemPosition();
		final View view = this.binding.list.getChildAt(0);
		if (view != null) {
			return new ScrollState(position,view.getTop());
		} else {
			return new ScrollState(position, 0);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		Log.d(Config.LOGTAG, "ConversationsOverviewFragment.onStart()");
		if (activity.xmppConnectionService != null) {
			refresh();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.d(Config.LOGTAG, "ConversationsOverviewFragment.onResume()");
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (MenuDoubleTabUtil.shouldIgnoreTap()) {
			return false;
		}
		switch (item.getItemId()) {
			case R.id.action_search:
				startActivity(new Intent(getActivity(), SearchActivity.class));
				return true;
			case R.id.action_easy_invite:
				selectAccountToStartEasyInvite();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void selectAccountToStartEasyInvite() {
		final List<Account> accounts = EasyOnboardingInvite.getSupportingAccounts(activity.xmppConnectionService);
		if (accounts.size() == 0) {
			//This can technically happen if opening the menu item races with accounts reconnecting or something
			Toast.makeText(getActivity(),R.string.no_active_accounts_support_this, Toast.LENGTH_LONG).show();
		} else if (accounts.size() == 1) {
			openEasyInviteScreen(accounts.get(0));
		} else {
			final AtomicReference<Account> selectedAccount = new AtomicReference<>(accounts.get(0));
			final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
			alertDialogBuilder.setTitle(R.string.choose_account);
			final String[] asStrings = Collections2.transform(accounts, a -> a.getJid().asBareJid().toEscapedString()).toArray(new String[0]);
			alertDialogBuilder.setSingleChoiceItems(asStrings, 0, (dialog, which) -> selectedAccount.set(accounts.get(which)));
			alertDialogBuilder.setNegativeButton(R.string.cancel, null);
			alertDialogBuilder.setPositiveButton(R.string.ok, (dialog, which) -> openEasyInviteScreen(selectedAccount.get()));
			alertDialogBuilder.create().show();
		}
	}

	private void openEasyInviteScreen(final Account account) {
		EasyOnboardingInviteActivity.launch(account, activity);
	}

	@Override
	void refresh() {
		if (this.binding == null || this.activity == null) {
			Log.d(Config.LOGTAG,"ConversationsOverviewFragment.refresh() skipped updated because view binding or activity was null");
			return;
		}
		this.activity.xmppConnectionService.populateWithOrderedConversations(this.conversations);
		Conversation removed = this.swipedConversation.peek();
		if (removed != null) {
			if (removed.isRead()) {
				this.conversations.remove(removed);
			} else {
				pendingActionHelper.execute();
			}
		}
		this.conversationHorizontalAdapter.notifyDataSetChanged();
		this.conversationAdapter.notifyDataSetChanged();
		ScrollState scrollState = pendingScrollState.pop();
		if (scrollState != null) {
			setScrollPosition(scrollState);
		}
		filter();
		updateAppBar();
	}

	private void updateAppBar() {
		String connectionStatus = "";
		switch (this.activity.xmppConnectionService.getAccounts().get(0).getStatus()) {
			case DISABLED:
				connectionStatus = getString(R.string.account_status_disabled);
				break;
			case CONNECTING:
				connectionStatus = getString(R.string.account_status_connecting);
				break;
			case NO_INTERNET:
				connectionStatus = getString(R.string.account_status_no_internet);
				break;
			case OFFLINE:
				connectionStatus = getString(R.string.account_status_offline);
				break;
			case SERVER_NOT_FOUND:
				connectionStatus = getString(R.string.account_status_not_found);
				break;
		}
//		activity.getSupportActionBar().setSubtitle(connectionStatus);
		final TextView contactStatus = activity.findViewById(R.id.contact_status);
		contactStatus.setText(connectionStatus.isEmpty()?getString(R.string.account_status_online):connectionStatus);
	}

	private void setScrollPosition(ScrollState scrollPosition) {
		if (scrollPosition != null) {
			LinearLayoutManager layoutManager = (LinearLayoutManager) binding.horizontalContactsList.getLayoutManager();
			layoutManager.scrollToPositionWithOffset(scrollPosition.position, scrollPosition.offset);
		}
	}
	protected void filter() {
		if (activity.xmppConnectionServiceBound) {
			this.filterContacts();
		}
	}
	protected void filterContacts() {
		this.contacts.clear();
		final Account account = this.activity.xmppConnectionService.getAccounts().get(0);
		if (account.getStatus() != Account.State.DISABLED) {
			this.contacts.addAll(account.getRoster().getContacts());
		}
		Collections.sort(this.contacts);
	}

	private void switchToConversation(Conversation conversation){
		Intent intent = new Intent(this.activity, ConversationsActivity.class);
		intent.setAction(ConversationsActivity.ACTION_VIEW_CONVERSATION);
		intent.putExtra(ConversationsActivity.EXTRA_CONVERSATION, conversation.getUuid());
		intent.setFlags(intent.getFlags() | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
	}
	protected void switchToConversation(Contact contact) {
		Conversation conversation = activity.xmppConnectionService.findOrCreateConversation(contact.getAccount(), contact.getJid(), false, true);
		switchToConversation(conversation);
	}
	protected void switchToConversationDoNotAppend(Contact contact, String body) {
		Conversation conversation = activity.xmppConnectionService.findOrCreateConversation(contact.getAccount(), contact.getJid(), false, true);
		activity.switchToConversationDoNotAppend(conversation, body, false);
	}

	private void hiddenMenuItem(Menu menu) {
		final MenuItem actionOngoingCall = menu.findItem(R.id.action_ongoing_call);
		actionOngoingCall.setVisible(false);
		final MenuItem actionCall = menu.findItem(R.id.action_call);
		actionCall.setVisible(false);
		final MenuItem actionSecurity = menu.findItem(R.id.action_security);
		actionSecurity.setVisible(false);
		final MenuItem actionAttachFile = menu.findItem(R.id.action_attach_file);
		actionAttachFile.setVisible(false);
		final MenuItem actionContactDetails = menu.findItem(R.id.action_contact_details);
		actionContactDetails.setVisible(false);
		final MenuItem actionMucDetails = menu.findItem(R.id.action_muc_details);
		actionMucDetails.setVisible(false);
		final MenuItem actionInvite = menu.findItem(R.id.action_invite);
		actionInvite.setVisible(false);
		final MenuItem actionSearch = menu.findItem(R.id.action_search);
		actionSearch.setVisible(false);
		final MenuItem actionArchive = menu.findItem(R.id.action_archive);
		actionArchive.setVisible(false);
	}

	protected void clearHistoryDialog(final Conversation conversation) {
		final androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(activity);
		builder.setTitle(getString(R.string.clear_conversation_history));
		final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_clear_history, null);
		final CheckBox endConversationCheckBox = dialogView.findViewById(R.id.end_conversation_checkbox);
		endConversationCheckBox.setVisibility(View.GONE);
		builder.setView(dialogView);
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton(getString(R.string.confirm), (dialog, which) -> {
			activity.xmppConnectionService.clearConversationHistory(conversation);
			refresh();
		});
		builder.create().show();
	}

	private void togglePinned(Conversation conversation) {
		final boolean pinned = conversation.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, false);
		conversation.setAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, !pinned);
		activity.xmppConnectionService.updateConversation(conversation);
		activity.invalidateOptionsMenu();
		refresh();
	}

	protected void muteConversationDialog(final Conversation conversation) {
		final androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(activity);
		builder.setTitle(R.string.disable_notifications);
		final int[] durations = getResources().getIntArray(R.array.mute_options_durations);
		final CharSequence[] labels = new CharSequence[durations.length];
		for (int i = 0; i < durations.length; ++i) {
			if (durations[i] == -1) {
				labels[i] = getString(R.string.until_further_notice);
			} else {
				labels[i] = TimeFrameUtils.resolve(activity, 1000L * durations[i]);
			}
		}
		builder.setItems(labels, (dialog, which) -> {
			final long till;
			if (durations[which] == -1) {
				till = Long.MAX_VALUE;
			} else {
				till = System.currentTimeMillis() + (durations[which] * 1000L);
			}
			conversation.setMutedTill(till);
			activity.xmppConnectionService.updateConversation(conversation);
			refresh();
			activity.invalidateOptionsMenu();
		});
		builder.create().show();
	}

	public void unmuteConversation(final Conversation conversation) {
		conversation.setMutedTill(0);
		activity.xmppConnectionService.updateConversation(conversation);
		refresh();
		activity.invalidateOptionsMenu();
	}

}
