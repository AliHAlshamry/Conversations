package eu.siacs.auva.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import eu.siacs.auva.R;
import eu.siacs.auva.databinding.DialogJoinConferenceBinding;
import eu.siacs.auva.ui.adapter.KnownHostsAdapter;
import eu.siacs.auva.ui.interfaces.OnBackendConnected;
import eu.siacs.auva.ui.util.DelayedHintHelper;

public class JoinConferenceDialog extends DialogFragment implements OnBackendConnected {

	private static final String PREFILLED_JID_KEY = "prefilled_jid";
	private static final String ACCOUNTS_LIST_KEY = "activated_accounts_list";
	private JoinConferenceDialogListener mListener;
	private KnownHostsAdapter knownHostsAdapter;

	public static JoinConferenceDialog newInstance(String prefilledJid, List<String> accounts) {
		JoinConferenceDialog dialog = new JoinConferenceDialog();
		Bundle bundle = new Bundle();
		bundle.putString(PREFILLED_JID_KEY, prefilledJid);
		bundle.putStringArrayList(ACCOUNTS_LIST_KEY, (ArrayList<String>) accounts);
		dialog.setArguments(bundle);
		return dialog;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setRetainInstance(true);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
//		builder.setTitle(R.string.join_public_channel);
		DialogJoinConferenceBinding binding = DataBindingUtil.inflate(getActivity().getLayoutInflater(), R.layout.dialog_join_conference, null, false);
		DelayedHintHelper.setHint(R.string.channel_full_jid_example, binding.jid);
		this.knownHostsAdapter = new KnownHostsAdapter(getActivity(), R.layout.simple_list_item);
		//binding.jid.setAdapter(knownHostsAdapter);
		String prefilledJid = getArguments().getString(PREFILLED_JID_KEY);
		if (prefilledJid != null) {
			binding.jid.append(prefilledJid);
		}
		StartConversationActivity.populateAccountSpinner(getActivity(), getArguments().getStringArrayList(ACCOUNTS_LIST_KEY), binding.account);
		builder.setView(binding.getRoot());
//		builder.setPositiveButton(R.string.join, null);
//		builder.setNegativeButton(R.string.cancel, null);
		AlertDialog dialog = builder.create();
		dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		dialog.show();
		WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
		layoutParams.copyFrom(dialog.getWindow().getAttributes());
		layoutParams.width = (int) (348 * this.getResources().getDisplayMetrics().density);
		layoutParams.height =  layoutParams.WRAP_CONTENT;
		dialog.getWindow().setAttributes(layoutParams);
		binding.cancelButton.setOnClickListener(v ->dialog.dismiss());
		binding.joinButton.setOnClickListener(v -> {
			mListener.onJoinDialogPositiveClick(dialog, binding.account, binding.accountJidLayout, binding.jid, binding.bookmark.isChecked());
			dialog.dismiss();
		});
		dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> mListener.onJoinDialogPositiveClick(dialog, binding.account, binding.accountJidLayout, binding.jid, binding.bookmark.isChecked()));
		binding.jid.setOnEditorActionListener((v, actionId, event) -> {
			mListener.onJoinDialogPositiveClick(dialog, binding.account, binding.accountJidLayout, binding.jid, binding.bookmark.isChecked());
			return true;
		});
		return dialog;
	}

	@Override
	public void onBackendConnected() {
		refreshKnownHosts();
	}

	private void refreshKnownHosts() {
		Activity activity = getActivity();
		if (activity instanceof XmppActivity) {
			Collection<String> hosts = ((XmppActivity) activity).xmppConnectionService.getKnownConferenceHosts();
			this.knownHostsAdapter.refresh(hosts);
		}
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			mListener = (JoinConferenceDialogListener) context;
		} catch (ClassCastException e) {
			throw new ClassCastException(context.toString()
					+ " must implement JoinConferenceDialogListener");
		}
	}

	@Override
	public void onDestroyView() {
		Dialog dialog = getDialog();
		if (dialog != null && getRetainInstance()) {
			dialog.setDismissMessage(null);
		}
		super.onDestroyView();
	}

	@Override
	public void onStart() {
		super.onStart();
		final Activity activity = getActivity();
		if (activity instanceof XmppActivity && ((XmppActivity) activity).xmppConnectionService != null) {
			refreshKnownHosts();
		}
	}

	public interface JoinConferenceDialogListener {
		void onJoinDialogPositiveClick(Dialog dialog, Spinner spinner, TextInputLayout jidLayout, AutoCompleteTextView jid, boolean isBookmarkChecked);
	}
}
