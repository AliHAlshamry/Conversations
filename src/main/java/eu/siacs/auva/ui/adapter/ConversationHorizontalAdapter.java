package eu.siacs.auva.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import eu.siacs.auva.R;
import eu.siacs.auva.databinding.ConversationHorizontalListRowBinding;
import eu.siacs.auva.entities.Conversation;
import eu.siacs.auva.ui.util.AvatarWorkerTask;

public class ConversationHorizontalAdapter extends RecyclerView.Adapter<ConversationHorizontalAdapter.ConversationViewHolder> {

    private final List<Conversation> conversations;
    private OnConversationClickListener listener;

    public ConversationHorizontalAdapter(List<Conversation> conversations) {
        this.conversations = conversations;
    }


    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ConversationViewHolder(DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.conversation_horizontal_list_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder viewHolder, int position) {
        Conversation conversation = conversations.get(position);
        if (conversation == null) {
            return;
        }
        if(conversation.getContact().getShownStatus().name().equals("ONLINE")&&conversation.getMode() == Conversation.MODE_SINGLE){
            viewHolder.binding.onlineTag.setVisibility(View.VISIBLE);
            conversation.alwaysNotify();
        }
        if(conversation.getMode() == Conversation.MODE_MULTI)
            viewHolder.binding.onlineTag.setVisibility(View.INVISIBLE);
        AvatarWorkerTask.loadAvatar(conversation, viewHolder.binding.conversationImage, R.dimen.avatar_on_conversation_horizontal_overview);
        viewHolder.itemView.setOnClickListener(v -> listener.onConversationClick(v, conversation));
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    public void setConversationClickListener(OnConversationClickListener listener) {
        this.listener = listener;
    }

    public interface OnConversationClickListener {
        void onConversationClick(View view, Conversation conversation);
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final ConversationHorizontalListRowBinding binding;

        private ConversationViewHolder(ConversationHorizontalListRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

    }

}
