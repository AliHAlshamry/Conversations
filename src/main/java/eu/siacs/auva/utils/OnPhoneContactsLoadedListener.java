package eu.siacs.auva.utils;

import android.os.Bundle;

import java.util.List;

public interface OnPhoneContactsLoadedListener {
	void onPhoneContactsLoaded(List<Bundle> phoneContacts);
}
