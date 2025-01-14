package com.bitflaker.lucidsourcekit.main.dreamjournal;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.view.inputmethod.EditorInfo.IME_ACTION_DONE;
import static java.util.UUID.randomUUID;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import com.bitflaker.lucidsourcekit.R;
import com.bitflaker.lucidsourcekit.database.MainDatabase;
import com.bitflaker.lucidsourcekit.general.Tools;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DreamJournalContentEditor extends Fragment {
    private OnContinueButtonClicked mContinueButtonClicked;
    private OnCloseButtonClicked mCloseButtonClicked;
    private ConstraintLayout topHeading;
    private EditText title, description;
    private ScrollView editorScroller;
    private ImageButton continueButton, addRecording, closeEditor, addTags, currentPlayingImageButton;
    private MaterialButton dateTime;
    private JournalInMemoryManager journalManger;
    private String journalEntryId;
    private boolean isRecordingRunning;
    public static final int REQUEST_AUDIO_PERMISSION_CODE = 1;
    private MediaRecorder mRecorder;
    private String audioFName;
    private RecordingData currentRecording;
    private MainDatabase db;
    private List<String> allAvailableTags;
    private MediaPlayer mPlayer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_dream_journal_content_editor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
//        Tools.makeStatusBarTransparent(DreamJournalEditor.this);

        db = MainDatabase.getInstance(getContext());
        topHeading = getView().findViewById(R.id.csl_dj_top_bar);
        title = getView().findViewById(R.id.txt_dj_title_dream);
        description = getView().findViewById(R.id.txt_dj_description_dream);
        editorScroller = getView().findViewById(R.id.scrl_editor_scroll);
        continueButton = getView().findViewById(R.id.btn_dj_continue_to_ratings);
        dateTime = getView().findViewById(R.id.btn_dj_date);
        addRecording = getView().findViewById(R.id.btn_dj_add_recording);
        addTags = getView().findViewById(R.id.btn_dj_add_tag);
        closeEditor = getView().findViewById(R.id.btn_dj_close_editor);

        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);
        DateFormat dtf = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        DateFormat tf = DateFormat.getTimeInstance(DateFormat.SHORT);
        JournalInMemory entry = journalManger.getEntry(journalEntryId);
        dateTime.setText(dtf.format(entry.getTime().getTime()));

//        RelativeLayout.LayoutParams lParams = (RelativeLayout.LayoutParams) editorScroller.getLayoutParams();
//        lParams.setMargins(0, Tools.getStatusBarHeight(this), 0, 0);
//        editorScroller.setLayoutParams(lParams);

        setupCloseButton();
        setupTagsEditor(entry);
        setupDateTimePicker(df, dtf, tf, entry);
        setupRecordingsEditor(entry);
        setupContinueButton();

        title.requestFocus();
        // TODO: description has to lose focus and then clicked in again in order to fully get rid of the edittext scrolling => CHANGE!
    }

    private void setupContinueButton() {
        continueButton.setOnClickListener(e -> {
            if(mContinueButtonClicked != null){
                mContinueButtonClicked.onEvent();
            }
        });
    }

    private void setupCloseButton() {
        closeEditor.setOnClickListener(e -> new AlertDialog.Builder(getContext(), Tools.getThemeDialog()).setTitle("Discard changes").setMessage("Do you really want to discard all changes")
                .setPositiveButton(getResources().getString(R.string.yes), (dialog, which) -> {
                    journalManger.discardEntry(journalEntryId);
                    if(mCloseButtonClicked != null){
                        mCloseButtonClicked.onEvent();
                    }
                })
                .setNegativeButton(getResources().getString(R.string.no), null)
                .show());
    }

    private void setupRecordingsEditor(JournalInMemory entry) {
        addRecording.setOnClickListener(e -> {
            final BottomSheetDialog bsd = new BottomSheetDialog(getContext(), R.style.BottomSheetDialog_Dark);
            bsd.setContentView(R.layout.fragment_recording);

            ImageButton recordAudio = bsd.findViewById(R.id.btn_dj_record_audio);
            LinearLayout recsList = bsd.findViewById(R.id.ll_dj_recs_list);
            LinearLayout recsEntryList = bsd.findViewById(R.id.ll_dj_recs_entry_list);
            RelativeLayout recNow = bsd.findViewById(R.id.rl_dj_recording_audio);
            TextView noRecordingsFound = bsd.findViewById(R.id.txt_dj_no_recs_found);
            TextView recordingText = bsd.findViewById(R.id.txt_dj_recording);
            ImageButton recContinuePause = bsd.findViewById(R.id.btn_dj_pause_continue_recording);
            ImageButton recStop = bsd.findViewById(R.id.btn_dj_stop_recording);

            showStoredRecordings(entry, recsEntryList, noRecordingsFound);
            setupStartRecordingButton(recordAudio, recsList, recNow, recordingText, recContinuePause);
            setupStopRecordingButton(recsList, recsEntryList, recNow, noRecordingsFound, recStop);
            setupPauseContinueRecordingButton(recordingText, recContinuePause);

            bsd.setOnDismissListener(e1 -> {
                if(isRecordingRunning) {
                    storeRecording(recsEntryList, recsList, recNow, noRecordingsFound);
                    isRecordingRunning = false;
                }
            });

            bsd.show();
        });
    }

    private void showStoredRecordings(JournalInMemory entry, LinearLayout recsEntryList, TextView noRecordingsFound) {
        if(entry.getAudioRecordings().size() == 0) {
            noRecordingsFound.setVisibility(View.VISIBLE);
        }
        else {
            for (RecordingData recData : entry.getAudioRecordings()) {
                recsEntryList.addView(generateAudioEntry(recData, noRecordingsFound));
            }
        }
    }

    private void setupPauseContinueRecordingButton(TextView recordingText, ImageButton recContinuePause) {
        recContinuePause.setOnClickListener(e1 -> {
            if(isRecordingRunning) {
                isRecordingRunning = false;
                pauseRecordingAudio();
                recContinuePause.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_play_arrow_24, getContext().getTheme()));
                recordingText.setText(getResources().getString(R.string.recording_paused));
            }
            else {
                isRecordingRunning = true;
                continueRecordingAudio();
                recContinuePause.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_pause_24, getContext().getTheme()));
                recordingText.setText(getResources().getString(R.string.recording));
            }
        });
    }

    private void setupStopRecordingButton(LinearLayout recsList, LinearLayout recsEntryList, RelativeLayout recNow, TextView noRecordingsFound, ImageButton recStop) {
        recStop.setOnClickListener(e1 -> {
            storeRecording(recsEntryList, recsList, recNow, noRecordingsFound);
            noRecordingsFound.setVisibility(View.GONE);
            isRecordingRunning = false;
        });
    }

    private void setupStartRecordingButton(ImageButton recordAudio, LinearLayout recsList, RelativeLayout recNow, TextView recordingText, ImageButton recContinuePause) {
        recordAudio.setOnClickListener(e1 -> {
            stopCurrentPlaybackIfPlaying();
            recsList.setVisibility(View.GONE);
            recNow.setVisibility(View.VISIBLE);
            recContinuePause.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_pause_24, getContext().getTheme()));
            recordingText.setText(getResources().getString(R.string.recording));
            startRecordingAudio(recsList, recNow);
        });
    }

    private void setupDateTimePicker(DateFormat df, DateFormat dtf, DateFormat tf, JournalInMemory entry) {
        dateTime.setOnClickListener(e -> {
            final BottomSheetDialog bsd = new BottomSheetDialog(getContext(), R.style.BottomSheetDialog_Dark);
            bsd.setContentView(R.layout.fragment_date_change);

            MaterialButton changeDate = bsd.findViewById(R.id.btn_dj_change_date);
            MaterialButton changeTime = bsd.findViewById(R.id.btn_dj_change_time);

            changeDate.setText(df.format(entry.getTime().getTime()));
            changeTime.setText(tf.format(entry.getTime().getTime()));

            setupChangeDateButton(df, dtf, entry, changeDate);
            setupChangeTimeButton(dtf, tf, entry, changeTime);

            bsd.show();
        });
    }

    private void setupChangeTimeButton(DateFormat dtf, DateFormat tf, JournalInMemory entry, MaterialButton changeTime) {
        changeTime.setOnClickListener(e1 -> {
            new TimePickerDialog(getContext(), (timePickerFrom, hourFrom, minuteFrom) -> {
                Calendar time = entry.getTime();
                time.set(Calendar.HOUR_OF_DAY, hourFrom);
                time.set(Calendar.MINUTE, minuteFrom);
                entry.setTime(time);
                changeTime.setText(tf.format(time.getTime()));
                dateTime.setText(dtf.format(time.getTime()));
            }, entry.getTime().get(Calendar.HOUR_OF_DAY), entry.getTime().get(Calendar.MINUTE), true).show();
        });
    }

    private void setupChangeDateButton(DateFormat df, DateFormat dtf, JournalInMemory entry, MaterialButton changeDate) {
        changeDate.setOnClickListener(e1 -> {
            new DatePickerDialog(getContext(), (datePicker, year, monthOfYear, dayOfMonth) -> {
                Calendar date = entry.getTime();
                date.set(Calendar.YEAR, year);
                date.set(Calendar.MONTH, monthOfYear);
                date.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                entry.setTime(date);
                changeDate.setText(df.format(date.getTime()));
                dateTime.setText(dtf.format(date.getTime()));
            }, entry.getTime().get(Calendar.YEAR), entry.getTime().get(Calendar.MONTH), entry.getTime().get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void setupTagsEditor(JournalInMemory entry) {
        db.getJournalEntryTagDao().getAllTagTexts().subscribe((tagsTexts, throwable) -> {
            allAvailableTags = tagsTexts;
        });

        addTags.setOnClickListener(e -> {
            final BottomSheetDialog bsd = new BottomSheetDialog(getContext(), R.style.BottomSheetDialog_Dark);
            bsd.setContentView(R.layout.fragment_tags_editor);

            FlexboxLayout tagsContainer = bsd.findViewById(R.id.flx_dj_tags_to_add);
            AutoCompleteTextView tagAddBox = bsd.findViewById(R.id.txt_dj_tags_enter);

            showAllSetTags(entry, tagsContainer);
            tagAddBox.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, allAvailableTags));
            setupTagEditorEditText(entry, tagsContainer, tagAddBox);
            setupTagEditorSelection(entry, tagsContainer, tagAddBox);

            bsd.show();
        });
    }

    private void setupTagEditorSelection(JournalInMemory entry, FlexboxLayout tagsContainer, AutoCompleteTextView tagAddBox) {
        tagAddBox.setOnItemClickListener((adapterView, view1, i, l) -> {
            String enteredTag = tagAddBox.getText().toString();
            if(!entry.getTags().contains(enteredTag)){
                entry.addTag(enteredTag);
                Chip tagChip = generateTagChip(enteredTag);
                tagChip.setOnCloseIconClickListener(e1 -> {
                    entry.removeTag(tagChip.getText().toString());
                    tagsContainer.removeView(tagChip);
                });
                tagsContainer.addView(tagChip);
                tagAddBox.setText("");
            }
        });
    }

    private void setupTagEditorEditText(JournalInMemory entry, FlexboxLayout tagsContainer, AutoCompleteTextView tagAddBox) {
        tagAddBox.setOnEditorActionListener((textView, i, keyEvent) -> {
            String enteredTag = tagAddBox.getText().toString();
            if(i == IME_ACTION_DONE && !entry.getTags().contains(enteredTag) && enteredTag.length() > 0) {
                entry.addTag(enteredTag);
                Chip tagChip = generateTagChip(enteredTag);
                tagChip.setOnCloseIconClickListener(e1 -> {
                    entry.removeTag(tagChip.getText().toString());
                    tagsContainer.removeView(tagChip);
                });
                tagsContainer.addView(tagChip);
                tagAddBox.setText("");
            }
            return true;
        });
    }

    private void showAllSetTags(JournalInMemory entry, FlexboxLayout tagsContainer) {
        List<String> tags = entry.getTags();
        for (String tag : tags) {
            Chip tagChip = generateTagChip(tag);
            tagChip.setOnCloseIconClickListener(e1 -> {
                entry.removeTag(tagChip.getText().toString());
                tagsContainer.removeView(tagChip);
            });
            tagsContainer.addView(tagChip);
        }
    }

    private void stopCurrentPlaybackIfPlaying() {
        if(mPlayer != null && mPlayer.isPlaying()){
            stopCurrentPlayback();
        }
    }

    private void stopCurrentPlayback() {
        currentPlayingImageButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
        mPlayer.stop();
        mPlayer.release();
        mPlayer = null;
        currentPlayingImageButton = null;
    }

    private Chip generateTagChip(String text) {
        Chip tag = new Chip(getContext());
        tag.setText(text);
        LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lparams.leftMargin = Tools.dpToPx(getContext(), 3);
        lparams.rightMargin = Tools.dpToPx(getContext(), 3);
        tag.setLayoutParams(lparams);
        tag.setChipBackgroundColor(Tools.getAttrColorStateList(R.attr.slightElevated, getContext().getTheme()));
        tag.setTextColor(Tools.getAttrColorStateList(R.attr.primaryTextColor, getContext().getTheme()));
        tag.setCloseIconTint(ColorStateList.valueOf(getResources().getColor(R.color.white, getContext().getTheme())));
        tag.setCheckedIconVisible(false);
        tag.setCloseIconVisible(true);
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        return tag;
    }

    private void pauseRecordingAudio() {
        mRecorder.pause();
    }

    private void continueRecordingAudio() {
        mRecorder.resume();
    }

    private void startRecordingAudio(LinearLayout listLayout, RelativeLayout recordLayout) {
        if(ContextCompat.checkSelfPermission(getActivity().getApplicationContext(), RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED){
            isRecordingRunning = true;
            audioFName = getActivity().getFilesDir().getAbsolutePath();
            String filename = "/Recordings/recording_" + randomUUID() + ".aac";
            audioFName += filename;
            currentRecording = new RecordingData(audioFName);
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mRecorder.setAudioEncodingBitRate(128000);
            mRecorder.setAudioSamplingRate(44100);
            mRecorder.setOutputFile(audioFName);
            try {
                mRecorder.prepare();
                mRecorder.start();
            } catch (IOException e) {
                System.err.println("ERROR preparing audio recording: " + e.getMessage());
            }
        }
        else {
            listLayout.setVisibility(View.VISIBLE);
            recordLayout.setVisibility(View.GONE);
            ActivityCompat.requestPermissions(getActivity(), new String[]{ RECORD_AUDIO }, REQUEST_AUDIO_PERMISSION_CODE);
        }
    }

    private void stopRecordingAudio() {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
        audioFName = null;
    }

    private void storeRecording(LinearLayout recList, LinearLayout listLayout, RelativeLayout recordLayout, TextView noRecordingsFound) {
        stopRecordingAudio();
        MediaPlayer dataReader = new MediaPlayer();
        try {
            dataReader.setDataSource(currentRecording.getFilepath());
            dataReader.prepare();
            currentRecording.setRecordingLength(dataReader.getDuration());
        } catch (IOException e) {
            e.printStackTrace();
            currentRecording.setRecordingLength(0);
        }
        currentRecording.setRecordingTime(Calendar.getInstance());
        journalManger.getEntry(journalEntryId).addAudioRecording(currentRecording);
        listLayout.setVisibility(View.VISIBLE);
        recordLayout.setVisibility(View.GONE);
        recList.addView(generateAudioEntry(currentRecording, noRecordingsFound));
        currentRecording = null;
    }

    private LinearLayout generateAudioEntry(RecordingData currentRecording, TextView noRecordingsFound) {
        int dp48 = Tools.dpToPx(getContext(), 48);
        int dp20 = Tools.dpToPx(getContext(), 20);
        int dp10 = Tools.dpToPx(getContext(), 10);
        int dp8 = Tools.dpToPx(getContext(), 8);
        int dp5 = Tools.dpToPx(getContext(), 5);

        LinearLayout entryContainer = new LinearLayout(getContext());
        LinearLayout.LayoutParams lParamsEntryContainer = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lParamsEntryContainer.setMargins(dp20, dp5, dp20, dp5);
        entryContainer.setLayoutParams(lParamsEntryContainer);
        entryContainer.setOrientation(LinearLayout.HORIZONTAL);
        entryContainer.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.rounded_spinner, getContext().getTheme()));
        entryContainer.setBackgroundTintList(Tools.getAttrColorStateList(R.attr.slightElevated, getContext().getTheme()));
        entryContainer.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton playButton = new ImageButton(getContext());
        LinearLayout.LayoutParams lParamsPlayButton = new LinearLayout.LayoutParams(dp48, dp48);
        lParamsPlayButton.setMargins(dp8, 0, 0, 0);
        playButton.setLayoutParams(lParamsPlayButton);
        playButton.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_play_arrow_24, getContext().getTheme()));
        playButton.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.rounded_spinner, getContext().getTheme()));
        playButton.setBackgroundTintList(Tools.getAttrColorStateList(R.attr.slightElevated, getContext().getTheme()));
        playButton.setOnClickListener(e -> handlePlayPauseMediaPlayer(currentRecording, playButton));
        entryContainer.addView(playButton);

        LinearLayout labelsContainer = new LinearLayout(getContext());
        LinearLayout.LayoutParams lParamsLabelsContainer = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT);
        lParamsLabelsContainer.setMargins(dp10, dp8, 0, dp8);
        lParamsLabelsContainer.weight = 1;
        labelsContainer.setLayoutParams(lParamsLabelsContainer);
        labelsContainer.setOrientation(LinearLayout.VERTICAL);
        entryContainer.addView(labelsContainer);

        TextView heading = new TextView(getContext());
        heading.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        heading.setText("Recording");
        heading.setTextColor(Tools.getAttrColorStateList(R.attr.primaryTextColor, getContext().getTheme()));
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        heading.setTypeface(null, Typeface.BOLD);
        labelsContainer.addView(heading);

        TextView timestamp = new TextView(getContext());
        timestamp.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
        DateFormat tf = DateFormat.getTimeInstance(DateFormat.SHORT);
        timestamp.setText(df.format(currentRecording.getRecordingTime().getTime()) + " • " + tf.format(currentRecording.getRecordingTime().getTime()));
        timestamp.setTextColor(Tools.getAttrColorStateList(R.attr.secondaryTextColor, getContext().getTheme()));
        timestamp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        labelsContainer.addView(timestamp);

        TextView duration = new TextView(getContext());
        LinearLayout.LayoutParams lParamsDuration = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lParamsDuration.setMargins(0, 0, dp10, 0);
        duration.setLayoutParams(lParamsDuration);
        int seconds = (int)(currentRecording.getRecordingLength() / 1000);
        int sec = seconds % 60;
        int min = (seconds / 60)%60;
        int hours = (seconds/60)/60;
        String secS = String.format(Locale.ENGLISH, "%02d" , sec);
        String minS = String.format(Locale.ENGLISH, "%02d" , min);
        String hoursS = String.format(Locale.ENGLISH, "%02d" , hours);
        duration.setText(hours > 0 ? hoursS + ":" : "" + minS + ":" + secS);
        duration.setTextColor(Tools.getAttrColorStateList(R.attr.secondaryTextColor, getContext().getTheme()));
        duration.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        entryContainer.addView(duration);

        ImageButton deleteButton = new ImageButton(getContext());
        LinearLayout.LayoutParams lParamsDeleteButton = new LinearLayout.LayoutParams(dp48, dp48);
        lParamsDeleteButton.setMargins(0, 0, dp8, 0);
        deleteButton.setLayoutParams(lParamsDeleteButton);
        deleteButton.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_cross_24, getContext().getTheme()));
        deleteButton.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.rounded_spinner, getContext().getTheme()));
        deleteButton.setBackgroundTintList(Tools.getAttrColorStateList(R.attr.slightElevated, getContext().getTheme()));
        deleteButton.setOnClickListener(e -> setupRecordingsDeleteDialog(currentRecording, entryContainer, playButton, noRecordingsFound));
        entryContainer.addView(deleteButton);

        return entryContainer;
    }

    private void setupRecordingsDeleteDialog(RecordingData currentRecording, LinearLayout entryContainer, ImageButton playButton, TextView noRecordingsFound) {
        new AlertDialog.Builder(getContext(), Tools.getThemeDialog()).setTitle(getResources().getString(R.string.recording_delete_header)).setMessage(getResources().getString(R.string.recording_delete_message))
                .setPositiveButton(getResources().getString(R.string.yes), (dialog, which) -> {
                    if(currentPlayingImageButton == playButton){ stopCurrentPlayback(); }
                    File audio = new File(currentRecording.getFilepath());
                    if(audio.delete()) {
                        ((LinearLayout) entryContainer.getParent()).removeView(entryContainer);
                        JournalInMemory journalInMemory = journalManger.getEntry(journalEntryId);
                        journalInMemory.removeAudioRecording(currentRecording);
                        if(journalInMemory.getAudioRecordings().size() == 0) {
                            noRecordingsFound.setVisibility(View.VISIBLE);
                        }
                    }
                })
                .setNegativeButton(getResources().getString(R.string.no), null)
                .show();
    }

    private void handlePlayPauseMediaPlayer(RecordingData currentRecording, ImageButton playButton) {
        if(mPlayer != null && mPlayer.isPlaying() && currentPlayingImageButton == playButton) {
            mPlayer.pause();
            currentPlayingImageButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
        }
        else if(mPlayer != null && !mPlayer.isPlaying() && currentPlayingImageButton == playButton) {
            mPlayer.start();
            currentPlayingImageButton.setImageResource(R.drawable.ic_baseline_pause_24);
        }
        else if(mPlayer != null && mPlayer.isPlaying()) {
            stopCurrentPlayback();
            playButton.setImageResource(R.drawable.ic_baseline_pause_24);
            setupAudioPlayer(currentRecording.getFilepath());
            currentPlayingImageButton = playButton;
        }
        else {
            playButton.setImageResource(R.drawable.ic_baseline_pause_24);
            setupAudioPlayer(currentRecording.getFilepath());
            currentPlayingImageButton = playButton;
        }
    }

    private void setupAudioPlayer(String audioFile) {
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(audioFile);
            mPlayer.setOnCompletionListener(e -> stopCurrentPlayback());
            mPlayer.prepare();
            mPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
            mPlayer = null;
        }
    }

    public void setJournalEntryId(String id) {
        journalEntryId = id;
        journalManger = JournalInMemoryManager.getInstance();
    }

    public interface OnContinueButtonClicked {
        void onEvent();
    }

    public void setOnContinueButtonClicked(OnContinueButtonClicked eventListener) {
        mContinueButtonClicked = eventListener;
    }

    public interface OnCloseButtonClicked {
        void onEvent();
    }

    public void setOnCloseButtonClicked(OnCloseButtonClicked eventListener) {
        mCloseButtonClicked = eventListener;
    }
}