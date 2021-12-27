package com.bitflaker.lucidsourcekit.main;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bitflaker.lucidsourcekit.R;
import com.bitflaker.lucidsourcekit.general.DatabaseWrapper;
import com.bitflaker.lucidsourcekit.general.JournalTypes;
import com.bitflaker.lucidsourcekit.general.Tools;
import com.bitflaker.lucidsourcekit.general.database.StoredJournalEntries;
import com.bitflaker.lucidsourcekit.general.database.values.DreamJournalEntriesList;
import com.bitflaker.lucidsourcekit.main.createjournalentry.AddTextEntry;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class DreamJournal extends Fragment {

    RecyclerView recyclerView;
    private TextView noEntryFound;
    private FloatingActionButton fabAdd, fabText, fabForms, fabAudio;
    private Animation fabOpen, fabClose, rotateForward, rotateBackward;
    private boolean isOpen = false;
    private DatabaseWrapper dbWrapper;
    private RecyclerViewAdapter recyclerViewAdapter;
    private ActivityResultLauncher<Intent> createEntryActivityResultLauncher;
    public ActivityResultLauncher<Intent> viewEntryActivityResultLauncher;
    private ImageButton sortEntries, filterEntries, resetFilterEntries;
    private int sortBy = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setupCreateResultLauncher();
        setupViewResultLauncher();
        return inflater.inflate(R.layout.fragment_dream_journal, container, false);
    }

    private void setupViewResultLauncher() {
        viewEntryActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if(data.hasExtra("action")) {
                            String action = data.getStringExtra("action");
                            if(action.equals("EDIT")) {
                                int position = data.getIntExtra("position", -1);
                                String selectedDate = data.getStringExtra("date");
                                String selectedTime = data.getStringExtra("time");
                                String title = data.getStringExtra("title");
                                String description = data.getStringExtra("description");
                                String quality = data.getStringExtra("quality");
                                String clarity = data.getStringExtra("clarity");
                                String mood = data.getStringExtra("mood");
                                String[] dreamTypes = data.getStringArrayExtra("dreamTypes");
                                String[] tags = data.getStringArrayExtra("tags");
                                String[] recordedAudios = data.getStringArrayExtra("recordings");
                                StoredJournalEntries entry = new StoredJournalEntries(-1, selectedDate, selectedTime, title, description, quality, clarity, mood);
                                recyclerViewAdapter.changeEntryAt(position, entry, tags, dreamTypes, recordedAudios);
                                recyclerViewAdapter.notifyItemChanged(position);
                                recyclerView.scrollToPosition(position);
                            }
                            else if (action.equals("DELETE")) {
                                int position = data.getIntExtra("position", -1);
                                recyclerViewAdapter.removeEntryAt(position);
                                recyclerViewAdapter.notifyItemRemoved(position);
                                recyclerViewAdapter.notifyItemRangeChanged(position, recyclerViewAdapter.getItemCount());
                            }
                        }
                    }
                });
    }

    private void setupCreateResultLauncher() {
        createEntryActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        int entryId = data.getIntExtra("entryId", -1);
                        String selectedDate = data.getStringExtra("date");
                        String selectedTime = data.getStringExtra("time");
                        String title = data.getStringExtra("title");
                        String description = data.getStringExtra("description");
                        String quality = data.getStringExtra("quality");
                        String clarity = data.getStringExtra("clarity");
                        String mood = data.getStringExtra("mood");
                        String[] dreamTypes = data.getStringArrayExtra("dreamTypes");
                        String[] tags = data.getStringArrayExtra("tags");
                        String[] recordedAudios = data.getStringArrayExtra("recordings");
                        StoredJournalEntries entry = new StoredJournalEntries(entryId, selectedDate, selectedTime, title, description, quality, clarity, mood);
                        recyclerViewAdapter.addEntry(entry, tags, dreamTypes, recordedAudios);
                        recyclerViewAdapter.notifyItemInserted(0);
                        recyclerView.scrollToPosition(0);
                        recyclerViewAdapter.notifyItemRangeChanged(0, recyclerViewAdapter.getItemCount());
                    }
                });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        noEntryFound = getView().findViewById(R.id.txt_no_entries);
        noEntryFound.setText(Html.fromHtml("<span><big><big><strong>Uhh...</strong></big></big><br />" + getContext().getResources().getString(R.string.empty_dream_journal) + "</span>", Html.FROM_HTML_MODE_COMPACT));

        recyclerView = getView().findViewById(R.id.recycler_view);
        dbWrapper = new DatabaseWrapper(getContext());

        DreamJournalEntriesList entries = dbWrapper.getJournalEntries();

        recyclerViewAdapter = new RecyclerViewAdapter(this, getContext(), entries);
        fabAdd = getView().findViewById(R.id.btn_add_journal_entry);
        fabText = getView().findViewById(R.id.fab_text);
        fabAudio = getView().findViewById(R.id.fab_audio);
        fabForms = getView().findViewById(R.id.fab_forms);
        sortEntries = getView().findViewById(R.id.btn_sort);
        filterEntries = getView().findViewById(R.id.btn_filter);
        resetFilterEntries = getView().findViewById(R.id.btn_filter_off);
        fabOpen = AnimationUtils.loadAnimation(getContext(), R.anim.add_open);
        fabClose = AnimationUtils.loadAnimation(getContext(),R.anim.add_close);
        rotateForward = AnimationUtils.loadAnimation(getContext(),R.anim.rotate_forward);
        rotateBackward = AnimationUtils.loadAnimation(getContext(),R.anim.rotate_backward);

        recyclerView.setAdapter(recyclerViewAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        fabAdd.setOnClickListener(view13 -> animateFab());
        fabText.setOnClickListener(view12 -> {
            showJournalCreator(JournalTypes.Text);
        });
        fabAudio.setOnClickListener(view1 -> {
            showJournalCreator(JournalTypes.Audio);
        });
        fabForms.setOnClickListener(view1 -> {
            showJournalCreator(JournalTypes.Forms);
        });

        sortEntries.setOnClickListener(e -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.ThemedDialog);
            builder.setTitle("Sort entries by");
            String[] sortOrder = {"timestamp - newest first", "timestamp - oldest first", "title - A to Z", "title - Z to A", "description - A to Z", "description - Z to A"};
            builder.setSingleChoiceItems(sortOrder, sortBy, (dialog, which) -> {
                sortBy = which;
                recyclerViewAdapter.sortEntries(sortBy);
                dialog.dismiss();
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        });

        filterEntries.setOnClickListener(e -> {
            FilterDialog fd = new FilterDialog(getActivity(), Tools.getUniqueOnly(recyclerViewAdapter.getAllTags()), recyclerViewAdapter.getCurrentFilter());
            fd.setOnClickPositiveButton(g -> {
                AppliedFilter af = fd.getFilters();
                if(!AppliedFilter.isEmptyFilter(af)){
                    recyclerViewAdapter.filter(af);
                    resetFilterEntries.setVisibility(View.VISIBLE);
                }
                else if (resetFilterEntries.getVisibility() == View.VISIBLE){
                    resetFilterEntries.setVisibility(View.GONE);
                    recyclerViewAdapter.resetFilters();
                }
                checkForEntries();
                fd.dismiss();
            });
            fd.show();
        });

        resetFilterEntries.setOnClickListener(e -> {
            resetFilterEntries.setVisibility(View.GONE);
            recyclerViewAdapter.resetFilters();
            checkForEntries();
        });

        checkForEntries();
    }

    private void checkForEntries() {
        if (recyclerViewAdapter.getItemCount() == 0) {
            noEntryFound.setVisibility(View.VISIBLE);
        }
        else if (noEntryFound.getVisibility() == View.VISIBLE){
            noEntryFound.setVisibility(View.GONE);
        }
    }

    private void showJournalCreator(JournalTypes forms) {
        animateFab();
        Intent intent = new Intent(getContext(), AddTextEntry.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("type", forms.ordinal());
        createEntryActivityResultLauncher.launch(intent);
    }

    private void animateFab(){
        if (isOpen){
            fabAdd.startAnimation(rotateForward);
            fabText.startAnimation(fabClose);
            fabAudio.startAnimation(fabClose);
            fabForms.startAnimation(fabClose);
            fabText.setClickable(false);
            fabAudio.setClickable(false);
            fabForms.setClickable(false);
            isOpen=false;
        }
        else {
            fabAdd.startAnimation(rotateBackward);
            fabText.startAnimation(fabOpen);
            fabAudio.startAnimation(fabOpen);
            fabForms.startAnimation(fabOpen);
            fabText.setClickable(true);
            fabAudio.setClickable(true);
            fabForms.setClickable(true);
            isOpen=true;
        }
    }

    public void pageChanged() {
        isOpen = false;
    }
}