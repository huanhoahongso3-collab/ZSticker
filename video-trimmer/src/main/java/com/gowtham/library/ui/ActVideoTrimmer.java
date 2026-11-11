package com.gowtham.library.ui;

import static com.gowtham.library.utils.VideoResKt.fromDisplayName;
import static com.gowtham.library.utils.VideoResKt.getVideoResNames;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.VideoEncoderSettings;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.akexorcist.localizationactivity.ui.LocalizationActivity;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.gowtham.library.R;
import com.gowtham.library.utils.CompressOption;
import com.gowtham.library.utils.FileUtilKt;
import com.gowtham.library.utils.LocaleHelper;
import com.gowtham.library.utils.LogMessage;
import com.gowtham.library.utils.TrimVideo;
import com.gowtham.library.utils.TrimVideoOptions;
import com.gowtham.library.utils.TrimmerUtils;
import com.gowtham.library.utils.VideoRes;
import com.gowtham.library.utils.VideoResKt;
import com.gowtham.library.utils.ViewUtil;
import com.kieronquinn.monetcompat.core.MonetCompat;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;

@UnstableApi
public class ActVideoTrimmer extends LocalizationActivity {

    private static final int PER_REQ_CODE = 115;
    private static final long MAX_CLIP_DURATION_MS = 5000L;
    private static final long MIN_CLIP_DURATION_MS = 1000L;

    private StyledPlayerView playerView;
    private ExoPlayer videoPlayer;
    private ImageView imagePlayPause;
    private View progressCircular;

    private TextView txtStartDuration, txtCurrentDuration, txtClipDuration, txtEndDuration;
    private MaterialButton btnDoneTrim;
    private MaterialButton resChangeSpinner;
    private Slider sliderOverview;
    private View layoutOverviewSlider;

    private RecyclerView rvFilmstrip;
    private TrimmerRangeOverlayView rangeOverlay;

    private long totalDurationMs = 1000L;
    private long startMs = 0L;
    private long endMs = 5000L;
    private boolean isVideoEnded = false;
    private long lastClickedTime = 0L;

    private int primaryColor;
    private boolean isMonetEnabled = false;

    private Uri fileUri;
    private Bundle bundle;
    private TrimVideoOptions trimVideoOptions;
    private boolean isCompressionEnabled = true;
    private VideoRes selectedRes;

    private androidx.appcompat.app.AlertDialog progressDialog;
    private LinearProgressIndicator dialogProgressIndicator;
    private TextView dialogProgressText;

    private Handler seekHandler;
    private Transformer transformer;
    private Runnable progressRunnable;
    private String outputPath;
    private boolean showFileLocationAlert = false;
    private String fileName;

    // Viewport window mapping for the scrollable filmstrip
    private int filmstripTotalPx = 0;
    private int filmstripVisiblePx = 0;
    private boolean isFilmstripScrolling = false;
    private boolean isOverviewDragging = false;

    private final Runnable updatePlayheadRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (videoPlayer != null && videoPlayer.getPlayWhenReady()) {
                    long currentMs = videoPlayer.getCurrentPosition();
                    if (txtCurrentDuration != null) {
                        txtCurrentDuration.setText(TrimmerUtils.formatSeconds(currentMs / 1000L));
                    }

                    // Loop seamlessly if exceeding endMs
                    if (currentMs >= endMs || currentMs < startMs) {
                        seekToMs(startMs);
                        currentMs = startMs;
                    }

                    // Update playhead on rangeOverlay
                    if (rangeOverlay != null) {
                        float playheadPercent = msToOverlayPercent(currentMs);
                        rangeOverlay.setPlayheadPercent(playheadPercent);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (seekHandler != null) {
                    seekHandler.postDelayed(this, 50);
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(
                this,
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        );
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        setContentView(R.layout.act_video_trimmer);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        bundle = getIntent().getExtras();
        if (bundle != null) {
            String videoOption = bundle.getString(TrimVideo.TRIM_VIDEO_OPTION);
            if (videoOption != null) {
                trimVideoOptions = new Gson().fromJson(videoOption, TrimVideoOptions.class);
            }
        }

        setUpToolBar(getSupportActionBar(), trimVideoOptions != null ? trimVideoOptions.title : null);
        toolbar.setNavigationOnClickListener(v -> finish());

        resolveThemeColors();
        setupWindowInsets();
    }

    private void resolveThemeColors() {
        try {
            isMonetEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("material_color_enabled", false);
            if (isMonetEnabled) {
                primaryColor = MonetCompat.getInstance().getAccentColor(this, null);
            } else {
                primaryColor = ContextCompat.getColor(this, R.color.trimmer_orange_primary);
            }
        } catch (Exception e) {
            primaryColor = ContextCompat.getColor(this, R.color.trimmer_orange_primary);
        }

        boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(!isDark);
        insetsController.setAppearanceLightNavigationBars(!isDark);
    }

    private void setupWindowInsets() {
        View rootView = findViewById(R.id.root_view);
        Toolbar toolbar = findViewById(R.id.toolbar);
        View bottomCard = findViewById(R.id.bottom_control_card);

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.displayCutout());

            toolbar.setPadding(statusBarInsets.left, statusBarInsets.top, statusBarInsets.right, 0);

            if (bottomCard != null) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) bottomCard.getLayoutParams();
                params.bottomMargin = navBarInsets.bottom + ViewUtil.dpToPx(12);
                params.leftMargin = navBarInsets.left + ViewUtil.dpToPx(12);
                params.rightMargin = navBarInsets.right + ViewUtil.dpToPx(12);
                bottomCard.setLayoutParams(params);
            }
            return insets;
        });
    }

    @Override
    protected void attachBaseContext(@NotNull Context base) {
        String lang = resolveAppLanguage(base);
        super.attachBaseContext(LocaleHelper.onAttach(base, lang));
    }

    private static String resolveAppLanguage(Context base) {
        try {
            String langCode = base.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("lang", "system");
            if (langCode != null && !"system".equals(langCode)) {
                return langCode;
            }
            Locale sysLocale = base.getResources().getConfiguration().getLocales().get(0);
            String sysLang = sysLocale != null ? sysLocale.getLanguage() : "en";
            for (String supported : new String[]{"en", "vi", "ru", "zh"}) {
                if (sysLang.startsWith(supported)) {
                    return supported;
                }
            }
        } catch (Exception ignored) {}
        return "en";
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        initViews();
        seekHandler = new Handler(Looper.getMainLooper());
        initPlayer();

        if (bundle != null) {
            String uriStr = bundle.getString(TrimVideo.TRIM_VIDEO_URI);
            if (uriStr != null) {
                fileUri = Uri.parse(uriStr);
            }
            isCompressionEnabled = bundle.getBoolean(TrimVideo.ENABLE_COMPRESSION, true);
        }

        if (!isCompressionEnabled && resChangeSpinner != null) {
            resChangeSpinner.setVisibility(View.GONE);
        }

        String selectVideoRes = savedInstanceState != null ? savedInstanceState.getString("selectedRes") : "";
        if (checkStoragePermission()) {
            setDataInView(selectVideoRes);
        }
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view_lib);
        imagePlayPause = findViewById(R.id.image_play_pause);
        progressCircular = findViewById(R.id.progress_circular);

        txtStartDuration = findViewById(R.id.txt_start_duration);
        txtCurrentDuration = findViewById(R.id.txt_current_duration);
        txtClipDuration = findViewById(R.id.txt_clip_duration);
        txtEndDuration = findViewById(R.id.txt_end_duration);

        btnDoneTrim = findViewById(R.id.btn_done_trim);
        resChangeSpinner = findViewById(R.id.txt_change_res);
        sliderOverview = findViewById(R.id.slider_overview);
        layoutOverviewSlider = findViewById(R.id.layout_overview_slider);

        rvFilmstrip = findViewById(R.id.rv_filmstrip);
        rangeOverlay = findViewById(R.id.range_overlay);

        if (rangeOverlay != null) {
            rangeOverlay.setAccentColor(primaryColor);
        }
        if (btnDoneTrim != null) {
            btnDoneTrim.setBackgroundColor(primaryColor);
            btnDoneTrim.setOnClickListener(v -> {
                if (SystemClock.elapsedRealtime() - lastClickedTime < 800) return;
                lastClickedTime = SystemClock.elapsedRealtime();
                trimVideo();
            });
        }

        findViewById(R.id.chip_start_container).setOnClickListener(v -> showManualTimeDialog(true));
        findViewById(R.id.chip_end_container).setOnClickListener(v -> showManualTimeDialog(false));

        setupQuickDurationChips();
    }

    private void setupQuickDurationChips() {
        setupDurationChip(findViewById(R.id.chip_dur_1s), 1000L);
        setupDurationChip(findViewById(R.id.chip_dur_2s), 2000L);
        setupDurationChip(findViewById(R.id.chip_dur_3s), 3000L);
        setupDurationChip(findViewById(R.id.chip_dur_4s), 4000L);
        setupDurationChip(findViewById(R.id.chip_dur_5s), 5000L);
    }

    private void setupDurationChip(View chip, long durationMs) {
        if (chip != null) {
            chip.setOnClickListener(v -> applyClipDuration(durationMs));
        }
    }

    private void applyClipDuration(long durationMs) {
        durationMs = Math.max(MIN_CLIP_DURATION_MS, Math.min(durationMs, Math.min(MAX_CLIP_DURATION_MS, totalDurationMs)));
        long newEnd = startMs + durationMs;
        if (newEnd > totalDurationMs) {
            newEnd = totalDurationMs;
            startMs = Math.max(0L, newEnd - durationMs);
        }
        endMs = newEnd;
        syncAllComponentsFromMs(true);
    }

    private void setUpToolBar(ActionBar actionBar, String title) {
        try {
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(true);
                actionBar.setTitle(title != null ? title : getString(R.string.txt_edt_video));
            }
        } catch (Exception e) {
            Log.e("ActVideoTrimmer", "setUpToolBar: ", e);
        }
    }

    private void initPlayer() {
        try {
            videoPlayer = new ExoPlayer.Builder(this).build();
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerView.setPlayer(videoPlayer);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MOVIE)
                    .build();
            videoPlayer.setAudioAttributes(audioAttributes, true);
            videoPlayer.setVolume(0f);
        } catch (Exception e) {
            Log.e("ActVideoTrimmer", "initPlayer: ", e);
        }
    }

    private void setDataInView(String selectVideoRes) {
        try {
            setSelectedVideoRes(selectVideoRes);

            Executors.newSingleThreadExecutor().execute(() -> {
                long durationSec = TrimmerUtils.getDuration(ActVideoTrimmer.this, fileUri);
                totalDurationMs = Math.max(1000L, durationSec * 1000L);

                runOnUiThread(() -> {
                    if (progressCircular != null) progressCircular.setVisibility(View.GONE);
                    imagePlayPause.setOnClickListener(v -> onVideoClicked());
                    Objects.requireNonNull(playerView.getVideoSurfaceView()).setOnClickListener(v -> onVideoClicked());

                    initTrimData();
                    buildMediaSource();
                    setupTwoTierTimeline();
                    setUpResChanger();
                    Toast.makeText(ActVideoTrimmer.this, getString(R.string.txt_select_part_toast), Toast.LENGTH_LONG).show();
                });
            });
        } catch (Exception e) {
            Log.e("ActVideoTrimmer", "setDataInView: ", e);
        }
    }

    private void setSelectedVideoRes(String selectVideoRes) {
        try {
            Pair<Integer, Integer> wh = TrimmerUtils.getVideoRes(this, fileUri);
            selectedRes = (selectVideoRes != null && !selectVideoRes.isEmpty()) ?
                    fromDisplayName(selectVideoRes) : TrimmerUtils.classifyResolution(wh.first, wh.second);
            if (resChangeSpinner != null) {
                resChangeSpinner.setText(selectedRes.getDisplayName());
            }
        } catch (Exception e) {
            Log.e("ActVideoTrimmer", "setSelectedVideoRes: ", e);
        }
    }

    private void setUpResChanger() {
        if (resChangeSpinner != null) {
            resChangeSpinner.setOnClickListener(v -> showResolutionMenu(resChangeSpinner));
        }
    }

    private void showResolutionMenu(View anchorView) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        List<String> resolutions = getVideoResNames(this, fileUri);
        for (String resolution : resolutions) {
            popupMenu.getMenu().add(resolution);
        }

        popupMenu.setOnMenuItemClickListener(menuItem -> {
            selectedRes = fromDisplayName(menuItem.getTitle().toString());
            if (resChangeSpinner != null) {
                resChangeSpinner.setText(selectedRes.getDisplayName());
            }
            return true;
        });
        popupMenu.show();
    }

    private void initTrimData() {
        if (trimVideoOptions != null) {
            fileName = trimVideoOptions.fileName;
            showFileLocationAlert = trimVideoOptions.showFileLocationAlert;
        }

        startMs = 0L;
        endMs = Math.min(totalDurationMs, MAX_CLIP_DURATION_MS);
    }

    private void buildMediaSource() {
        try {
            DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
            MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(fileUri));
            videoPlayer.addMediaSource(mediaSource);
            videoPlayer.prepare();
            videoPlayer.setPlayWhenReady(true);
            videoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                    imagePlayPause.setVisibility(playWhenReady ? View.GONE : View.VISIBLE);
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_ENDED) {
                        isVideoEnded = true;
                        imagePlayPause.setVisibility(View.VISIBLE);
                    } else if (state == Player.STATE_READY) {
                        isVideoEnded = false;
                        imagePlayPause.setVisibility(View.GONE);
                        startProgress();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupTwoTierTimeline() {
        // Setup Tier 1: Full-video overview slider for videos > 5s
        if (totalDurationMs > MAX_CLIP_DURATION_MS) {
            layoutOverviewSlider.setVisibility(View.VISIBLE);
            sliderOverview.setValueFrom(0f);
            sliderOverview.setValueTo((float) totalDurationMs);
            sliderOverview.setValue(0f);
            sliderOverview.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                @Override
                public void onStartTrackingTouch(@NonNull Slider slider) {
                    isOverviewDragging = true;
                }

                @Override
                public void onStopTrackingTouch(@NonNull Slider slider) {
                    isOverviewDragging = false;
                    long targetMs = (long) slider.getValue();
                    panClipToCenter(targetMs);
                }
            });
            sliderOverview.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser && isOverviewDragging) {
                    panClipToCenter((long) value);
                }
            });
        } else {
            layoutOverviewSlider.setVisibility(View.GONE);
        }

        // Setup Tier 2: Zoomed scrollable filmstrip (RecyclerView)
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvFilmstrip.setLayoutManager(layoutManager);

        // Calculate total frames: e.g. 1 frame per 1.5 seconds, min 10 frames
        int frameCount = Math.max(10, (int) (totalDurationMs / 1500L));
        FilmstripAdapter adapter = new FilmstripAdapter(this, fileUri, totalDurationMs, frameCount);
        rvFilmstrip.setAdapter(adapter);

        rvFilmstrip.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                isFilmstripScrolling = (newState != RecyclerView.SCROLL_STATE_IDLE);
                if (!isFilmstripScrolling) {
                    seekToMs(startMs);
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (isFilmstripScrolling) {
                    int offset = recyclerView.computeHorizontalScrollOffset();
                    int range = recyclerView.computeHorizontalScrollRange() - recyclerView.computeHorizontalScrollExtent();
                    if (range > 0) {
                        float scrollPercent = (float) offset / (float) range;
                        long currentClipDuration = endMs - startMs;
                        long maxStart = Math.max(0L, totalDurationMs - currentClipDuration);
                        long newStart = Math.round(scrollPercent * maxStart);
                        startMs = newStart;
                        endMs = newStart + currentClipDuration;
                        updateTimeLabels();
                        updateOverviewSlider();
                    }
                }
            }
        });

        // Setup TrimmerRangeOverlayView
        float minSpan = (float) MIN_CLIP_DURATION_MS / (float) Math.min(totalDurationMs, MAX_CLIP_DURATION_MS);
        float maxSpan = 1.0f;
        rangeOverlay.setRange(0f, 1f, minSpan, maxSpan);
        rangeOverlay.setOnTrimRangeListener((startPercent, endPercent, isFinal) -> {
            long viewportDuration = Math.min(totalDurationMs, MAX_CLIP_DURATION_MS);
            long clipSpan = Math.round((endPercent - startPercent) * viewportDuration);
            clipSpan = Math.max(MIN_CLIP_DURATION_MS, Math.min(clipSpan, MAX_CLIP_DURATION_MS));

            long newEnd = startMs + clipSpan;
            if (newEnd > totalDurationMs) {
                newEnd = totalDurationMs;
                startMs = Math.max(0L, newEnd - clipSpan);
            }
            endMs = newEnd;

            updateTimeLabels();
            updateOverviewSlider();

            if (isFinal) {
                seekToMs(startMs);
            }
        });

        syncAllComponentsFromMs(true);
    }

    private void panClipToCenter(long centerMs) {
        long currentClipDuration = endMs - startMs;
        long newStart = Math.max(0L, Math.min(centerMs - (currentClipDuration / 2L), totalDurationMs - currentClipDuration));
        startMs = newStart;
        endMs = newStart + currentClipDuration;
        syncAllComponentsFromMs(true);
    }

    private void syncAllComponentsFromMs(boolean scrollFilmstrip) {
        updateTimeLabels();
        updateOverviewSlider();

        if (scrollFilmstrip && rvFilmstrip != null) {
            int range = rvFilmstrip.computeHorizontalScrollRange() - rvFilmstrip.computeHorizontalScrollExtent();
            long maxStart = Math.max(1L, totalDurationMs - (endMs - startMs));
            float ratio = (float) startMs / (float) maxStart;
            int targetScrollX = Math.round(ratio * range);
            rvFilmstrip.scrollBy(targetScrollX - rvFilmstrip.computeHorizontalScrollOffset(), 0);
        }

        if (rangeOverlay != null) {
            long viewportDuration = Math.min(totalDurationMs, MAX_CLIP_DURATION_MS);
            float spanPercent = (float) (endMs - startMs) / (float) viewportDuration;
            float minSpan = (float) MIN_CLIP_DURATION_MS / (float) viewportDuration;
            rangeOverlay.setRange(0f, spanPercent, minSpan, 1f);
        }

        seekToMs(startMs);
    }

    private void updateTimeLabels() {
        if (txtStartDuration != null) {
            txtStartDuration.setText(TrimmerUtils.formatSeconds(startMs / 1000L));
        }
        if (txtEndDuration != null) {
            txtEndDuration.setText(TrimmerUtils.formatSeconds(endMs / 1000L));
        }
        if (txtClipDuration != null) {
            float clipSec = (endMs - startMs) / 1000f;
            txtClipDuration.setText(String.format(Locale.getDefault(), getString(R.string.txt_clip_duration_format), clipSec));
        }
        if (txtCurrentDuration != null) {
            txtCurrentDuration.setText(TrimmerUtils.formatSeconds(startMs / 1000L));
        }
    }

    private void updateOverviewSlider() {
        if (sliderOverview != null && layoutOverviewSlider.getVisibility() == View.VISIBLE && !isOverviewDragging) {
            sliderOverview.setValue((float) Math.min(totalDurationMs, Math.max(0L, startMs)));
        }
    }

    private float msToOverlayPercent(long currentMs) {
        long currentClipDuration = Math.max(1L, endMs - startMs);
        if (currentMs < startMs) return 0f;
        if (currentMs > endMs) return rangeOverlay.getEndPercent();
        float fraction = (float) (currentMs - startMs) / (float) currentClipDuration;
        float overlayStart = rangeOverlay.getStartPercent();
        float overlayEnd = rangeOverlay.getEndPercent();
        return overlayStart + fraction * (overlayEnd - overlayStart);
    }

    private void onVideoClicked() {
        try {
            if (videoPlayer == null) return;
            if (isVideoEnded) {
                seekToMs(startMs);
                videoPlayer.setPlayWhenReady(true);
                return;
            }
            long curPos = videoPlayer.getCurrentPosition();
            if (curPos >= endMs || curPos < startMs) {
                seekToMs(startMs);
            }
            videoPlayer.setPlayWhenReady(!videoPlayer.getPlayWhenReady());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void seekToMs(long ms) {
        if (videoPlayer != null) {
            videoPlayer.seekTo(Math.max(0L, ms));
        }
    }

    private void showManualTimeDialog(boolean isStart) {
        try {
            if (videoPlayer != null) {
                videoPlayer.setPlayWhenReady(false);
            }

            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manual_time, null);
            TextView title = dialogView.findViewById(R.id.dialog_title);
            TextView subtitle = dialogView.findViewById(R.id.dialog_subtitle);
            EditText input = dialogView.findViewById(R.id.et_time_input);

            title.setText(isStart ? R.string.txt_start_time : R.string.txt_end_time);
            subtitle.setText(String.format("00:00 — %s", TrimmerUtils.formatSeconds(totalDurationMs / 1000L)));

            final long currentValMs = isStart ? startMs : endMs;
            input.setText(TrimmerUtils.formatSeconds(currentValMs / 1000L));
            input.setSelection(input.getText().length());

            androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setView(dialogView)
                    .create();

            // Nudge buttons inside dialog
            dialogView.findViewById(R.id.btn_nudge_minus_1s).setOnClickListener(v -> adjustManualInput(input, -1.0f));
            dialogView.findViewById(R.id.btn_nudge_minus_half).setOnClickListener(v -> adjustManualInput(input, -0.5f));
            dialogView.findViewById(R.id.btn_nudge_plus_half).setOnClickListener(v -> adjustManualInput(input, +0.5f));
            dialogView.findViewById(R.id.btn_nudge_plus_1s).setOnClickListener(v -> adjustManualInput(input, +1.0f));

            dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
            dialogView.findViewById(R.id.btn_apply).setOnClickListener(v -> {
                long parsedMs = parseTimeInputToMs(input.getText().toString(), currentValMs);
                applyManualTimeSelection(isStart, parsedMs);
                dialog.dismiss();
            });

            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void adjustManualInput(EditText input, float deltaSec) {
        try {
            String text = input.getText().toString();
            long currentMs = parseTimeInputToMs(text, 0L);
            long newMs = Math.max(0L, Math.min(totalDurationMs, currentMs + (long) (deltaSec * 1000L)));
            input.setText(TrimmerUtils.formatSeconds(newMs / 1000L));
            input.setSelection(input.getText().length());
        } catch (Exception ignored) {}
    }

    private long parseTimeInputToMs(String rawInput, long fallbackMs) {
        if (rawInput == null) return fallbackMs;
        String text = rawInput.trim().replace("s", "").replace("S", "").trim();
        if (text.isEmpty()) return fallbackMs;
        try {
            if (text.contains(":")) {
                String[] parts = text.split(":");
                long totalSec = 0L;
                for (String part : parts) {
                    totalSec = totalSec * 60L + Long.parseLong(part.trim());
                }
                return totalSec * 1000L;
            } else {
                return (long) (Double.parseDouble(text) * 1000.0);
            }
        } catch (Exception e) {
            return fallbackMs;
        }
    }

    private void applyManualTimeSelection(boolean isStart, long targetMs) {
        if (isStart) {
            long newStart = Math.max(0L, Math.min(targetMs, totalDurationMs - MIN_CLIP_DURATION_MS));
            long newEnd = endMs;
            if (newEnd < newStart + MIN_CLIP_DURATION_MS) {
                newEnd = Math.min(totalDurationMs, newStart + MIN_CLIP_DURATION_MS);
            }
            if (newEnd - newStart > MAX_CLIP_DURATION_MS) {
                newEnd = Math.min(totalDurationMs, newStart + MAX_CLIP_DURATION_MS);
            }
            startMs = newStart;
            endMs = newEnd;
        } else {
            long newEnd = Math.max(MIN_CLIP_DURATION_MS, Math.min(targetMs, totalDurationMs));
            long newStart = startMs;
            if (newEnd < newStart + MIN_CLIP_DURATION_MS) {
                newStart = Math.max(0L, newEnd - MIN_CLIP_DURATION_MS);
            }
            if (newEnd - newStart > MAX_CLIP_DURATION_MS) {
                newStart = Math.max(0L, newEnd - MAX_CLIP_DURATION_MS);
            }
            startMs = newStart;
            endMs = newEnd;
        }
        syncAllComponentsFromMs(true);
    }

    @UnstableApi
    private void trimVideo() {
        if (!isCompressionEnabled) {
            if (videoPlayer != null) {
                videoPlayer.setPlayWhenReady(false);
            }
            Intent intent = new Intent();
            String sourcePath = (fileUri != null && fileUri.getPath() != null) ? fileUri.getPath() : String.valueOf(fileUri);
            intent.putExtra(TrimVideo.TRIMMED_VIDEO_PATH, sourcePath);
            intent.putExtra(TrimVideo.TRIM_START_MS, startMs);
            intent.putExtra(TrimVideo.TRIM_END_MS, endMs);
            setResult(RESULT_OK, intent);
            finish();
            return;
        }

        outputPath = getFileName();
        if (videoPlayer != null) {
            videoPlayer.setPlayWhenReady(false);
        }
        showProcessingDialog();

        androidx.media3.common.MediaItem mediaItem =
                new androidx.media3.common.MediaItem.Builder()
                        .setUri(fileUri)
                        .setClippingConfiguration(
                                new androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                                        .setStartPositionMs(startMs)
                                        .setEndPositionMs(endMs)
                                        .build())
                        .build();

        Pair<Integer, Integer> videoRes = TrimmerUtils.getVideoRes(this, fileUri);
        int originalWidth = videoRes.first;
        int originalHeight = videoRes.second;

        float reduceRatio = VideoResKt.getDownScaleRatio(this, fileUri, selectedRes);
        int finalWidth = originalWidth;
        int finalHeight = originalHeight;
        if (reduceRatio < 1f && reduceRatio > 0f) {
            finalWidth = Math.round(originalWidth - (originalWidth * reduceRatio));
            finalHeight = Math.round(originalHeight - (originalHeight * reduceRatio));
        }

        EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem)
                .setEffects(new Effects(ImmutableList.of(),
                        ImmutableList.of(Presentation.createForWidthAndHeight(finalWidth, finalHeight, Presentation.LAYOUT_SCALE_TO_FIT))))
                .build();

        long bitRate = TrimmerUtils.getBitRate(this, fileUri);
        if (isCompressionEnabled) {
            HashMap<VideoRes, Long> videoResMap = TrimmerUtils.getResBitRate(this, fileUri);
            Long compressionBitRate = videoResMap.get(selectedRes);
            bitRate = compressionBitRate != null ? compressionBitRate : bitRate;
        }

        transformer = new Transformer.Builder(this)
                .addListener(transformerListener)
                .setEncoderFactory(new DefaultEncoderFactory.Builder(this)
                        .setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder()
                                .setBitrate(Math.toIntExact(bitRate))
                                .build())
                        .build())
                .build();

        transformer.start(editedMediaItem, outputPath);

        ProgressHolder progressHolder = new ProgressHolder();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    @Transformer.ProgressState int progressState = transformer.getProgress(progressHolder);
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        int progress = progressHolder.progress;
                        if (dialogProgressText != null) {
                            dialogProgressText.setText(String.format(Locale.getDefault(), "%s %d%%", getString(R.string.txt_trimming_video), progress));
                        }
                        if (dialogProgressIndicator != null) {
                            dialogProgressIndicator.setIndeterminate(false);
                            dialogProgressIndicator.setProgress(progress);
                        }
                    }
                    if (progressState != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        mainHandler.postDelayed(this, 300);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        mainHandler.post(progressRunnable);
    }

    private final Transformer.Listener transformerListener = new Transformer.Listener() {
        @Override
        public void onCompleted(Composition composition, ExportResult result) {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            if (showFileLocationAlert) {
                showLocationAlert();
            } else {
                Intent intent = new Intent();
                intent.putExtra(TrimVideo.TRIMMED_VIDEO_PATH, outputPath);
                setResult(RESULT_OK, intent);
                finish();
            }
        }

        @Override
        public void onError(Composition composition, ExportResult result, ExportException exception) {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            Toast.makeText(ActVideoTrimmer.this, R.string.txt_failed_to_trim, Toast.LENGTH_SHORT).show();
        }
    };

    private void showProcessingDialog() {
        try {
            View view = LayoutInflater.from(this).inflate(R.layout.alert_convert, null);
            dialogProgressText = view.findViewById(R.id.txt_process);
            dialogProgressIndicator = view.findViewById(R.id.progress_trimmer);
            View btnCancel = view.findViewById(R.id.txt_cancel);

            progressDialog = new MaterialAlertDialogBuilder(this)
                    .setView(view)
                    .setCancelable(false)
                    .create();

            btnCancel.setOnClickListener(v -> {
                progressDialog.dismiss();
                if (transformer != null) {
                    transformer.cancel();
                }
            });
            progressDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showLocationAlert() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.open_file_location)
                .setPositiveButton(R.string.yes, (d, which) -> {
                    Intent chooser = new Intent(Intent.ACTION_GET_CONTENT);
                    chooser.addCategory(Intent.CATEGORY_OPENABLE);
                    chooser.setDataAndType(Uri.parse(outputPath), "*/*");
                    startActivity(chooser);
                })
                .setNegativeButton(R.string.no, (d, which) -> d.dismiss())
                .setOnDismissListener(d -> {
                    Intent intent = new Intent();
                    intent.putExtra(TrimVideo.TRIMMED_VIDEO_PATH, outputPath);
                    setResult(RESULT_OK, intent);
                    finish();
                })
                .show();
    }

    private String getFileName() {
        File dir = getExternalFilesDir("TrimmedVideo");
        if (dir == null) dir = new File(getCacheDir(), "TrimmedVideo");
        if (!dir.exists()) dir.mkdirs();

        Calendar cal = Calendar.getInstance();
        String fileDateTime = String.format(Locale.US, "%d_%d_%d_%d_%d_%d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));

        String fName = (fileName != null && !fileName.isEmpty()) ? fileName : "trimmed_video_";
        return new File(dir, fName + fileDateTime + ".mp4").getAbsolutePath();
    }

    private boolean checkStoragePermission() {
        try {
            if (fileUri != null) {
                String scheme = fileUri.getScheme();
                if (scheme == null || "file".equalsIgnoreCase(scheme)) {
                    File file = new File(fileUri.getPath() != null ? fileUri.getPath() : fileUri.toString());
                    if (file.exists() && file.canRead()) return true;
                }
                try (android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(fileUri, "r")) {
                    if (pfd != null) return true;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        String uri = FileUtilKt.getActualFileUri(this, fileUri);
        if (uri != null && new File(uri).canRead()) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void startProgress() {
        if (seekHandler != null) {
            seekHandler.post(updatePlayheadRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoPlayer != null) {
            videoPlayer.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (seekHandler != null) {
                seekHandler.removeCallbacksAndMessages(null);
            }
            if (videoPlayer != null) {
                videoPlayer.release();
            }
            if (transformer != null) {
                transformer.cancel();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
