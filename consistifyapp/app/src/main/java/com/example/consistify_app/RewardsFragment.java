package com.example.consistify_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class RewardsFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rewards, container, false);
        
        GamificationManager manager = new GamificationManager(requireContext());
        TextView tvCoins = view.findViewById(R.id.tv_current_coins);
        tvCoins.setText("Your FitCoins: " + manager.getFitCoins());
        
        return view;
    }
}
