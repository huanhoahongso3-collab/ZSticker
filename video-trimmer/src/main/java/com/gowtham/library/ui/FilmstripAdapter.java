package com.gowtham.library.ui;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.gowtham.library.R;

public class FilmstripAdapter extends RecyclerView.Adapter<FilmstripAdapter.FilmstripViewHolder> {

    private final Context context;
    private final Uri videoUri;
    private final int totalFrames;
    private final long intervalUs;
    private final int totalFilmstripWidthPx;

    public FilmstripAdapter(Context context, Uri videoUri, long totalDurationMs, int totalFrames, int totalFilmstripWidthPx) {
        this.context = context;
        this.videoUri = videoUri;
        this.totalFrames = Math.max(1, totalFrames);
        long totalDurationUs = Math.max(1L, totalDurationMs) * 1000L;
        this.intervalUs = totalDurationUs / this.totalFrames;
        this.totalFilmstripWidthPx = Math.max(this.totalFrames, totalFilmstripWidthPx);
    }

    @NonNull
    @Override
    public FilmstripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_filmstrip_frame, parent, false);
        return new FilmstripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FilmstripViewHolder holder, int position) {
        int startPx = Math.round((float) position * totalFilmstripWidthPx / totalFrames);
        int endPx = Math.round((float) (position + 1) * totalFilmstripWidthPx / totalFrames);
        int itemWidthPx = Math.max(1, endPx - startPx);

        ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
        if (lp != null && lp.width != itemWidthPx) {
            lp.width = itemWidthPx;
            holder.itemView.setLayoutParams(lp);
        }

        long frameTimeUs = (position * intervalUs) + (intervalUs / 2L);
        RequestOptions options = new RequestOptions()
                .frame(frameTimeUs)
                .override(140, 140)
                .centerCrop();

        Glide.with(context)
                .load(videoUri)
                .apply(options)
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .into(holder.imageView);
    }

    @Override
    public void onViewRecycled(@NonNull FilmstripViewHolder holder) {
        super.onViewRecycled(holder);
        Glide.with(context).clear(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return totalFrames;
    }

    static class FilmstripViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;

        FilmstripViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.img_frame);
        }
    }
}
