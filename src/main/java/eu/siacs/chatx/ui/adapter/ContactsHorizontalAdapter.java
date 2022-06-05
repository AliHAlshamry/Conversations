package eu.siacs.chatx.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import eu.siacs.chatx.R;
import eu.siacs.chatx.databinding.ContactsHorizontalListRowBinding;
import eu.siacs.chatx.entities.Contact;
import eu.siacs.chatx.entities.Presence;
import eu.siacs.chatx.ui.util.AvatarWorkerTask;

public class ContactsHorizontalAdapter extends RecyclerView.Adapter<ContactsHorizontalAdapter.ContactsViewHolder> {

    private final List<Contact> contacts;
    private OnContactClickListener listener;

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

    static class ContactsViewHolder extends RecyclerView.ViewHolder {
        private final ContactsHorizontalListRowBinding binding;

        private ContactsViewHolder(ContactsHorizontalListRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

    }

}
