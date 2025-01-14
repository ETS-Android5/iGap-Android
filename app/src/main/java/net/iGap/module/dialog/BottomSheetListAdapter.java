package net.iGap.module.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import net.iGap.G;
import net.iGap.R;
import net.iGap.databinding.CustomListItemBottomSheetBinding;

import java.util.List;

public class BottomSheetListAdapter extends RecyclerView.Adapter<BottomSheetListAdapter.ViewHolder> {

    private List<Integer> items;
    private List<String> itemsStr;
    private int range;

    @Nullable
    private final BottomSheetItemClickCallback itemClickCallback;

    public BottomSheetListAdapter(List<String> items, int range, @Nullable BottomSheetItemClickCallback itemClickCallback) {
        this.itemsStr = itemsStr;
        this.range = range;
        this.itemClickCallback = itemClickCallback;
    }

    public BottomSheetListAdapter(int range, List<Integer> items, @Nullable BottomSheetItemClickCallback itemClickCallback) {
        this.items = items;
        this.range = range;
        this.itemClickCallback = itemClickCallback;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        CustomListItemBottomSheetBinding binding = DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.custom_list_item_bottom_sheet, parent, false);
        binding.setCallback(itemClickCallback);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.setTitle(holder.itemView.getContext().getString(items.get(position)));
        holder.binding.setIcon(findRelevantIcon(position));
        holder.binding.setPosition(position);
        holder.binding.executePendingBindings();
        holder.binding.itemTitle.setSelected(true/*position < range*/);
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        final CustomListItemBottomSheetBinding binding;

        ViewHolder(@NonNull CustomListItemBottomSheetBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public String findRelevantIcon(int position) {
        int item = items.get(position);
        if (item == R.string.replay_item_dialog) {
            return G.context.getResources().getString(R.string.icon_reply);
        } else if (item == R.string.share_item_dialog ||
                item == R.string.share_link_item_dialog ||
                item == R.string.share_file_link ||
                item == R.string.share_image ||
                item == R.string.share_video_file) {
            return G.context.getResources().getString(R.string.icon_share);
        } else if (item == R.string.forward_item_dialog) {
            return G.context.getResources().getString(R.string.icon_forward);
        } else if (item == R.string.delete_item_dialog) {
            return G.context.getResources().getString(R.string.icon_delete);
        } else if (item == R.string.delete_from_storage) {
            return G.context.getResources().getString(R.string.icon_clear_history);
        } else if (item == R.string.save_to_gallery) {
            return G.context.getResources().getString(R.string.icon_gallery);
        } else if (item == R.string.save_to_Music) {
            return G.context.getResources().getString(R.string.icon_music_file);
        } else if (item == R.string.saveToDownload_item_dialog) {
            return G.context.getResources().getString(R.string.icon_download);
        } else if (item == R.string.copy_item_dialog) {
            return G.context.getResources().getString(R.string.icon_copy);
        } else if (item == R.string.edit_item_dialog) {
            return G.context.getResources().getString(R.string.icon_edit);
        } else if (item == R.string.PIN) {
            return G.context.getResources().getString(R.string.icon_pin_to_top);
        } else if (item == R.string.report) {
            return G.context.getResources().getString(R.string.icon_error);
        } else {
            return "";
        }
    }
}