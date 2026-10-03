package com.campus.lostfound.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.campus.lostfound.R;
import com.campus.lostfound.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
    private final List<ChatMessage> messages = new ArrayList<>();

    public void submitList(List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        holder.bind(messages.get(position));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout messageRoot;
        private final LinearLayout bubbleContainer;
        private final TextView textSender;
        private final TextView textMessage;
        private final TextView textTime;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            messageRoot = itemView.findViewById(R.id.message_root);
            bubbleContainer = itemView.findViewById(R.id.bubble_container);
            textSender = itemView.findViewById(R.id.text_sender);
            textMessage = itemView.findViewById(R.id.text_message);
            textTime = itemView.findViewById(R.id.text_time);
        }

        void bind(ChatMessage message) {
            boolean isYou = message.getSender().equals("you");

            textSender.setText(isYou ? R.string.chat_you : R.string.chat_owner);
            textMessage.setText(message.getText());

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            textTime.setText(sdf.format(new Date(message.getAt())));

            // Align bubble: you = right, other = left
            messageRoot.setGravity(isYou ? Gravity.END : Gravity.START);

            if (isYou) {
                bubbleContainer.setBackgroundResource(R.drawable.bg_chat_bubble_you);
                textSender.setTextColor(itemView.getContext().getColor(R.color.primary));
            } else {
                bubbleContainer.setBackgroundResource(R.drawable.bg_chat_bubble_other);
                textSender.setTextColor(itemView.getContext().getColor(R.color.secondary));
            }
        }
    }
}
