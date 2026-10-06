package com.example.weather;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Horizontal list of the next 24 hours. */
public class HourlyAdapter extends RecyclerView.Adapter<HourlyAdapter.VH> {

    private List<WeatherData.Hour> hours = new ArrayList<>();
    private boolean h24 = true;

    public void setData(List<WeatherData.Hour> hours, boolean h24) {
        this.hours = hours;
        this.h24 = h24;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hourly, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH vh, int position) {
        WeatherData.Hour h = hours.get(position);
        vh.hour.setText(position == 0 ? "Now" : WeatherUtils.hour(h.time, h24));
        vh.icon.setText(WeatherUtils.icon(h.code, h.isDay));
        vh.precip.setText(h.precipProb >= 20 ? h.precipProb + "%" : "");
        vh.temp.setText(WeatherUtils.deg(h.temp));
        // Highlight "Now" with the neon capsule
        vh.itemView.setBackgroundResource(position == 0 ? R.drawable.bg_capsule_now : R.drawable.bg_capsule);
    }

    @Override
    public int getItemCount() {
        return hours.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView hour, icon, precip, temp;

        VH(View v) {
            super(v);
            hour = v.findViewById(R.id.tvHour);
            icon = v.findViewById(R.id.tvHourIcon);
            precip = v.findViewById(R.id.tvHourPrecip);
            temp = v.findViewById(R.id.tvHourTemp);
        }
    }
}
