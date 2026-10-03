package com.campus.lostfound.adapter;

import android.net.Uri;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.campus.lostfound.R;
import com.campus.lostfound.model.Item;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ItemViewHolder> {

    public interface OnItemActionListener {
        void onContact(Item item);

        void onChat(Item item);

        void onDelete(Item item);
    }

    private final List<Item> items = new ArrayList<>();
    private final OnItemActionListener listener;
    private String currentUserId;

    public ItemAdapter(OnItemActionListener listener) {
        this.listener = listener;
    }

    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
        notifyDataSetChanged();
    }

    public void submitList(List<Item> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        Item item = items.get(position);
        holder.bind(item, listener, currentUserId);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        private final TextView textTypeBadge;
        private final TextView textMeta;
        private final TextView textTitle;
        private final TextView textDescription;
        private final TextView textLocation;
        private final TextView textDate;
        private final TextView textOwner;
        private final ImageView imageItem;
        private final MaterialButton buttonContact;
        private final MaterialButton buttonChat;
        private final MaterialButton buttonDelete;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            textTypeBadge = itemView.findViewById(R.id.text_type_badge);
            textMeta = itemView.findViewById(R.id.text_meta);
            textTitle = itemView.findViewById(R.id.text_title);
            textDescription = itemView.findViewById(R.id.text_description);
            textLocation = itemView.findViewById(R.id.text_location);
            textDate = itemView.findViewById(R.id.text_date);
            textOwner = itemView.findViewById(R.id.text_owner);
            imageItem = itemView.findViewById(R.id.image_item);
            buttonContact = itemView.findViewById(R.id.button_contact);
            buttonChat = itemView.findViewById(R.id.button_chat);
            buttonDelete = itemView.findViewById(R.id.button_delete);
        }

        void bind(Item item, OnItemActionListener listener, String currentUserId) {
            // Type badge: LOST or FOUND with distinct colors
            String type = item.getType() != null ? item.getType() : "Lost";
            textTypeBadge.setText(type.toUpperCase());
            if ("found".equalsIgnoreCase(type)) {
                textTypeBadge.setBackgroundResource(R.drawable.bg_tag_found);
                textTypeBadge.setTextColor(itemView.getContext().getColor(R.color.type_found));
            } else {
                textTypeBadge.setBackgroundResource(R.drawable.bg_tag_lost);
                textTypeBadge.setTextColor(itemView.getContext().getColor(R.color.type_lost));
            }

            // Category tag
            String category = item.getCategory() != null ? item.getCategory() : "";
            textMeta.setText(category.toUpperCase());

            textTitle.setText(item.getTitle());
            textDescription.setText(item.getDescription());
            textLocation.setText(item.getLocation());
            textDate.setText(item.getDate());
            textOwner.setText(item.getContactName());

            if (item.getImageUri() != null && !item.getImageUri().isEmpty()) {
                imageItem.setVisibility(View.VISIBLE);
                String imageUri = item.getImageUri();
                if (imageUri.startsWith("data:image/")) {
                    int separator = imageUri.indexOf(',');
                    if (separator > 0) {
                        byte[] imageBytes = Base64.decode(imageUri.substring(separator + 1), Base64.DEFAULT);
                        imageItem.setImageBitmap(BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length));
                    }
                } else {
                    imageItem.setImageURI(Uri.parse(imageUri));
                }
            } else {
                imageItem.setVisibility(View.GONE);
            }

            buttonContact.setOnClickListener(v -> listener.onContact(item));
            buttonChat.setOnClickListener(v -> listener.onChat(item));
            boolean isOwner = currentUserId != null && currentUserId.equals(item.getCreatorId());
            buttonDelete.setVisibility(isOwner ? View.VISIBLE : View.GONE);
            buttonDelete.setOnClickListener(v -> listener.onDelete(item));
        }
    }
}
