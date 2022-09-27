package eu.siacs.auva.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import eu.siacs.auva.R;
import eu.siacs.auva.databinding.ContactsHorizontalListRowBinding;
import eu.siacs.auva.entities.Contact;
import eu.siacs.auva.entities.Presence;
import eu.siacs.auva.ui.util.AvatarWorkerTask;

public class ContactsHorizontalAdapter extends RecyclerView.Adapter<ContactsHorizontalAdapter.ContactsViewHolder> {

    private final List<Contact> contacts;
    private OnContactClickListener listener;
    private OnContactLongClickListener longListener;

    public ContactsHorizontalAdapter(List<Contact> contacts) {
        this.contacts = contacts;
    }


    @NonNull
    @Override
    public ContactsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ContactsViewHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.contacts_horizontal_list_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ContactsViewHolder viewHolder, int position) {
        Contact contact = contacts.get(position);
            if (contact == null) {
                return;
            }
            if (contact.getShownStatus() == Presence.Status.ONLINE) {
                viewHolder.binding.onlineTag.setVisibility(View.VISIBLE);
            }
            viewHolder.binding.contactJid.setText(contact.getJid().getLocal());
            AvatarWorkerTask.loadAvatar(contact, viewHolder.binding.conversationImage, R.dimen.avatar_on_conversation_overview);
        viewHolder.itemView.setOnClickListener(v -> listener.onContactClick(v, contact));
        viewHolder.itemView.setOnLongClickListener(v -> {
            longListener.onContactLongClick(v, contact);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    public void setContactsClickListener(OnContactClickListener listener) {
        this.listener = listener;
    }

    public interface OnContactClickListener {
        void onContactClick(View view, Contact contact);
    }

    public void setContactLongClickListener(OnContactLongClickListener listener) {
        this.longListener = listener;
    }

    public interface OnContactLongClickListener {
        void onContactLongClick(View view, Contact contact);
    }

    static class ContactsViewHolder extends RecyclerView.ViewHolder {
        private final ContactsHorizontalListRowBinding binding;

        private ContactsViewHolder(ContactsHorizontalListRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

    }

}
