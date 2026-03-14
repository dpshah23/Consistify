package com.example.consistify_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ProfileFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        
        GamificationManager manager = new GamificationManager(requireContext());
        TextView tvLevel = view.findViewById(R.id.tv_profile_level);
        tvLevel.setText("Level: " + manager.getCurrentLevel());
        
        // Fetch current streak
        GamificationDatabaseHelper dbHelper = new GamificationDatabaseHelper(requireContext());
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT current_streak FROM user_profile WHERE id=1", null);
        int streak = 0;
        if (c.moveToFirst()) {
            streak = c.getInt(0);
        }
        c.close();
        
        TextView tvStreak = view.findViewById(R.id.tv_streak);
        tvStreak.setText(streak + " Days \uD83D\uDD25"); // Fire emoji

        return view;
    }
}
