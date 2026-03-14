package com.example.consistify_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class RewardsFragment extends Fragment {
    private TextView tvCoins;
    private GamificationManager manager;
    private Button btnSticker, btnWristband, btnTowel, btnShaker, btnJumpRope, btnPro;

    private final BroadcastReceiver statsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateRewardsUI();
        }
    };
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rewards, container, false);
        
        manager = new GamificationManager(requireContext());
        tvCoins = view.findViewById(R.id.tv_current_coins);
        
        btnSticker = view.findViewById(R.id.btn_reward_sticker);
        btnWristband = view.findViewById(R.id.btn_reward_wristband);
        btnTowel = view.findViewById(R.id.btn_reward_towel);
        btnShaker = view.findViewById(R.id.btn_reward_shaker);
        btnJumpRope = view.findViewById(R.id.btn_reward_jumprope);
        btnPro = view.findViewById(R.id.btn_reward_pro);
        
        setupRewardButton(btnSticker, "Sticker Pack", 200);
        setupRewardButton(btnWristband, "Consistify Wristband", 500);
        setupRewardButton(btnTowel, "Gym Towel", 900);
        setupRewardButton(btnShaker, "Premium Shaker", 1500);
        setupRewardButton(btnJumpRope, "Smart Jump Rope", 2500);
        setupRewardButton(btnPro, "1 Month Pro Plan", 5000);

        updateRewardsUI();
        
        return view;
    }

    private void setupRewardButton(Button btn, String itemName, int cost) {
        btn.setOnClickListener(v -> {
            if (manager.deductFitCoins(cost)) {
                Toast.makeText(requireContext(), itemName + " ordered successfully!", Toast.LENGTH_SHORT).show();
                updateRewardsUI(); // Refresh state for all buttons immediately
            } else {
                Toast.makeText(requireContext(), "Not enough FitCoins!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateRewardsUI() {
        if (!isAdded() || getContext() == null) return;
        int currentCoins = manager.getFitCoins();
        tvCoins.setText("Your FitCoins: " + currentCoins);
        
        updateButtonState(btnSticker, currentCoins, 200);
        updateButtonState(btnWristband, currentCoins, 500);
        updateButtonState(btnTowel, currentCoins, 900);
        updateButtonState(btnShaker, currentCoins, 1500);
        updateButtonState(btnJumpRope, currentCoins, 2500);
        updateButtonState(btnPro, currentCoins, 5000);
    }

    private void updateButtonState(Button btn, int currentCoins, int requiredCoins) {
        if (currentCoins >= requiredCoins) {
            btn.setEnabled(true);
            btn.setAlpha(1.0f);
        } else {
            btn.setEnabled(false);
            btn.setAlpha(0.5f);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(statsReceiver, new IntentFilter("STEPS_UPDATED"), Context.RECEIVER_NOT_EXPORTED);
            requireContext().registerReceiver(statsReceiver, new IntentFilter("STATS_UPDATED"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(statsReceiver, new IntentFilter("STEPS_UPDATED"));
            requireContext().registerReceiver(statsReceiver, new IntentFilter("STATS_UPDATED"));
        }
        updateRewardsUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(statsReceiver);
        } catch (IllegalArgumentException e) {
            // Ignored
        }
    }
}
