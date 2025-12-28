package com.ltrudu.sitewatcher.ui.about;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.ltrudu.sitewatcher.BuildConfig;
import com.ltrudu.sitewatcher.R;

/**
 * Fragment displaying application information.
 * Shows app version, description, developer info, and links.
 */
public class AboutFragment extends Fragment {

    private static final String GITHUB_URL = "https://github.com/ltrudu/SiteWatcher";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_about, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
    }

    private void initViews(View view) {
        // Set version text
        TextView tvVersion = view.findViewById(R.id.tvVersion);
        String versionText = getString(R.string.version, BuildConfig.VERSION_NAME);
        tvVersion.setText(versionText);

        // Set up source code button
        MaterialButton btnSourceCode = view.findViewById(R.id.btnSourceCode);
        btnSourceCode.setOnClickListener(v -> openGitHub());
    }

    /**
     * Opens the GitHub repository in a browser.
     */
    private void openGitHub() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL));
        startActivity(intent);
    }
}
