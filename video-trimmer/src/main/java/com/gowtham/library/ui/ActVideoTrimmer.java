package com.gowtham.library.ui;

import static com.gowtham.library.utils.VideoResKt.fromDisplayName;
import static com.gowtham.library.utils.VideoResKt.getVideoResNames;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
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
import androidx.core.graphics.ColorUtils;
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
import com.google.android.exoplayer2.SeekParameters;
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
    private TextView txtTotalVideoDuration;
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
    private long windowStartMs = 0L;
    private boolean isFilmstripScrolling = false;
    private boolean isProgrammaticScroll = false;
    private boolean isOverviewDragging = false;
    private boolean isRangeDragging = false;
    private boolean isUserScrubActive = false;
    private boolean wasPlayingBeforeScrub = true;
    private boolean hasRefinedDurationFromPlayer = false;
    private long lastLiveScrubElapsedMs = 0L;
    private long pendingScrubTargetMs = -1L;
    private MaterialButton chipDur1s, chipDur2s, chipDur3s, chipDur4s, chipDur5s;

    private final Runnable pendingScrubRunnable = new Runnable() {
        @Override
        public void run() {
            if (videoPlayer != null && isUserScrubbing() && pendingScrubTargetMs >= 0L) {
                lastLiveScrubElapsedMs = SystemClock.elapsedRealtime();
                videoPlayer.seekTo(pendingScrubTargetMs);
                pendingScrubTargetMs = -1L;
            }
        }
    };

    private final Runnable updatePlayheadRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (videoPlayer != null && videoPlayer.getPlayWhenReady() && !isUserScrubbing()) {
                    long currentMs = Math.max(0L, Math.min(totalDurationMs, videoPlayer.getCurrentPosition()));
                    if (txtCurrentDuration != null) {
                        txtCurrentDuration.setText(TrimmerUtils.formatMillisPrecise(currentMs));
                    }

                    // Only force an early loop if endMs is strictly before the end of the file;
                    // when endMs reaches the end of the video, let ExoPlayer play every frame to STATE_ENDED.
                    boolean trimEndsBeforeEof = endMs < (totalDurationMs - 45L);
                    if ((trimEndsBeforeEof && currentMs >= endMs) || currentMs < (startMs - 80L)) {
                        seekToMs(startMs);
                        currentMs = startMs;
                    }

                    // Update playhead on rangeOverlay
                    if (rangeOverlay != null) {
                        float playheadPercent = msToOverlayPercent(currentMs);
                        rangeOverlay.setPlayheadPercent(playheadPercent);
                    }

                    // Advance full-video scrubber along with playback (0% .. 100%)
                    setOverviewSliderValueSafe(currentMs);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (seekHandler != null) {
                    seekHandler.postDelayed(this, 33);
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

        resolveThemeColors();
        setUpToolBar(getSupportActionBar(), toolbar, trimVideoOptions != null ? trimVideoOptions.title : null);
        toolbar.setNavigationOnClickListener(v -> finish());

        setupWindowInsets();
    }

    private void resolveThemeColors() {
        boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        try {
            isMonetEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("material_color_enabled", false);
            if (isMonetEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    primaryColor = ContextCompat.getColor(
                            this,
                            isDark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600
                    );
                } else {
                    primaryColor = MonetCompat.getInstance().getAccentColor(this, null);
                }
            } else {
                primaryColor = ContextCompat.getColor(this, isDark ? R.color.trimmer_orange_primary_dark : R.color.trimmer_orange_primary);
            }
        } catch (Exception e) {
            primaryColor = ContextCompat.getColor(this, isDark ? R.color.trimmer_orange_primary_dark : R.color.trimmer_orange_primary);
        }

        if (isMonetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int n1_10 = ContextCompat.getColor(this, android.R.color.system_neutral1_10);
            int n1_50 = ContextCompat.getColor(this, android.R.color.system_neutral1_50);
            int n1_100 = ContextCompat.getColor(this, android.R.color.system_neutral1_100);
            int n1_800 = ContextCompat.getColor(this, android.R.color.system_neutral1_800);
            int n1_900 = ContextCompat.getColor(this, android.R.color.system_neutral1_900);
            int n1_1000 = ContextCompat.getColor(this, android.R.color.system_neutral1_1000);

            int surface = isDark
                    ? ColorUtils.blendARGB(n1_900, n1_1000, 0.40f)
                    : ColorUtils.blendARGB(n1_10, n1_50, 0.25f);
            int surfaceContainer = isDark
                    ? ColorUtils.blendARGB(n1_900, n1_800, 0.20f)
                    : ColorUtils.blendARGB(n1_50, n1_100, 0.20f);

            View rootView = findViewById(R.id.root_view);
            if (rootView != null) rootView.setBackgroundColor(surface);
            View appBar = findViewById(R.id.app_bar_layout);
            if (appBar != null) appBar.setBackgroundColor(surface);
            com.google.android.material.card.MaterialCardView bottomCard = findViewById(R.id.bottom_control_card);
            if (bottomCard != null) bottomCard.setCardBackgroundColor(surfaceContainer);
        }

        WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(!isDark);
        insetsController.setAppearanceLightNavigationBars(!isDark);
    }

    private int resolveThemeAttrColor(int attrRes) {
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(attrRes, tv, true)) {
            if (tv.resourceId != 0) {
                return ContextCompat.getColor(this, tv.resourceId);
            }
            return tv.data;
        }
        return Color.GRAY;
    }

    private void setupWindowInsets() {
        View rootView = findViewById(R.id.root_view);
        View appBarLayout = findViewById(R.id.app_bar_layout);
        View bottomCard = findViewById(R.id.bottom_control_card);

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.displayCutout());

            if (appBarLayout != null) {
                appBarLayout.setPadding(statusBarInsets.left, statusBarInsets.top, statusBarInsets.right, 0);
            }

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

        if (resChangeSpinner != null) {
            resChangeSpinner.setVisibility(isCompressionEnabled ? View.VISIBLE : View.GONE);
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
        txtTotalVideoDuration = findViewById(R.id.txt_total_video_duration);

        btnDoneTrim = findViewById(R.id.btn_done_trim);
        resChangeSpinner = findViewById(R.id.txt_change_res);
        sliderOverview = findViewById(R.id.slider_overview);
        layoutOverviewSlider = findViewById(R.id.layout_overview_slider);

        rvFilmstrip = findViewById(R.id.rv_filmstrip);
        rangeOverlay = findViewById(R.id.range_overlay);

        chipDur1s = findViewById(R.id.chip_dur_1s);
        chipDur2s = findViewById(R.id.chip_dur_2s);
        chipDur3s = findViewById(R.id.chip_dur_3s);
        chipDur4s = findViewById(R.id.chip_dur_4s);
        chipDur5s = findViewById(R.id.chip_dur_5s);

        int onPrimary = ColorUtils.calculateLuminance(primaryColor) > 0.5 ? Color.parseColor("#1C1B1F") : Color.WHITE;

        if (rangeOverlay != null) {
            rangeOverlay.setAccentColor(primaryColor);
        }
        if (btnDoneTrim != null) {
            btnDoneTrim.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            btnDoneTrim.setTextColor(onPrimary);
            btnDoneTrim.setIconTint(ColorStateList.valueOf(onPrimary));
            btnDoneTrim.setOnClickListener(v -> {
                if (SystemClock.elapsedRealtime() - lastClickedTime < 800) return;
                lastClickedTime = SystemClock.elapsedRealtime();
                trimVideo();
            });
        }
        if (txtClipDuration != null) {
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.RECTANGLE);
            badgeBg.setCornerRadius(999f);
            badgeBg.setColor(ColorUtils.setAlphaComponent(primaryColor, 32));
            txtClipDuration.setBackground(badgeBg);
            txtClipDuration.setTextColor(primaryColor);
        }
        if (sliderOverview != null) {
            sliderOverview.setTrackActiveTintList(ColorStateList.valueOf(primaryColor));
            sliderOverview.setThumbTintList(ColorStateList.valueOf(primaryColor));
            sliderOverview.setHaloTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(primaryColor, 56)));
            sliderOverview.setTrackInactiveTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(primaryColor, 48)));
        }

        findViewById(R.id.chip_start_container).setOnClickListener(v -> showManualTimeDialog(true));
        findViewById(R.id.chip_end_container).setOnClickListener(v -> showManualTimeDialog(false));

        setupQuickDurationChips();
        updateQuickDurationChipsState();
    }

    private void setupQuickDurationChips() {
        setupDurationChip(chipDur1s, 1000L);
        setupDurationChip(chipDur2s, 2000L);
        setupDurationChip(chipDur3s, 3000L);
        setupDurationChip(chipDur4s, 4000L);
        setupDurationChip(chipDur5s, 5000L);
    }

    private void setupDurationChip(MaterialButton chip, long durationMs) {
        if (chip != null) {
            chip.setOnClickListener(v -> applyClipDuration(durationMs));
        }
    }

    private long getEffectiveMinClipMs() {
        return Math.max(100L, Math.min(MIN_CLIP_DURATION_MS, totalDurationMs));
    }

    private void updateQuickDurationChipsState() {
        long currentDurationMs = Math.max(getEffectiveMinClipMs(), endMs - startMs);
        styleDurationChip(chipDur1s, 1000L, currentDurationMs);
        styleDurationChip(chipDur2s, 2000L, currentDurationMs);
        styleDurationChip(chipDur3s, 3000L, currentDurationMs);
        styleDurationChip(chipDur4s, 4000L, currentDurationMs);
        styleDurationChip(chipDur5s, 5000L, currentDurationMs);
    }

    private void styleDurationChip(MaterialButton chip, long chipDurationMs, long currentDurationMs) {
        if (chip == null) return;
        boolean isAvailable = totalDurationMs >= (chipDurationMs - 200L) || chipDurationMs == MIN_CLIP_DURATION_MS;
        chip.setEnabled(isAvailable);
        chip.setAlpha(isAvailable ? 1.0f : 0.38f);

        boolean isSelected = isAvailable && Math.abs(currentDurationMs - Math.min(chipDurationMs, totalDurationMs)) <= 250L;
        if (isSelected) {
            int onPrimary = ColorUtils.calculateLuminance(primaryColor) > 0.5 ? Color.parseColor("#1C1B1F") : Color.WHITE;
            chip.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            chip.setTextColor(onPrimary);
        } else {
            int surfaceHigh = resolveThemeAttrColor(com.google.android.material.R.attr.colorSurfaceContainerHigh);
            int onSurface = resolveThemeAttrColor(com.google.android.material.R.attr.colorOnSurface);
            chip.setBackgroundTintList(ColorStateList.valueOf(surfaceHigh));
            chip.setTextColor(onSurface);
        }
    }

    private void applyClipDuration(long durationMs) {
        long minClip = getEffectiveMinClipMs();
        durationMs = Math.max(minClip, Math.min(durationMs, Math.min(MAX_CLIP_DURATION_MS, totalDurationMs)));
        long newEnd = startMs + durationMs;
        if (newEnd > totalDurationMs) {
            newEnd = totalDurationMs;
            startMs = Math.max(0L, newEnd - durationMs);
        }
        endMs = newEnd;
        syncAllComponentsFromMs(true);
    }

    private void setUpToolBar(ActionBar actionBar, Toolbar toolbar, String title) {
        try {
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(true);
                actionBar.setTitle(title != null ? title : getString(R.string.txt_edt_video));
            }
            if (toolbar != null) {
                Drawable arrow = ContextCompat.getDrawable(this, R.drawable.trimmer_arrow_back_white);
                if (arrow != null) {
                    arrow = arrow.mutate();
                    arrow.setTint(primaryColor);

                    GradientDrawable circleBg = new GradientDrawable();
                    circleBg.setShape(GradientDrawable.OVAL);
                    circleBg.setColor(ColorUtils.setAlphaComponent(primaryColor, 30));
                    int sizePx = ViewUtil.dpToPx(40);
                    circleBg.setSize(sizePx, sizePx);

                    int padPx = ViewUtil.dpToPx(8);
                    LayerDrawable layered = new LayerDrawable(new Drawable[]{circleBg, arrow});
                    layered.setLayerInset(1, padPx, padPx, padPx, padPx);
                    toolbar.setNavigationIcon(layered);
                }
            }
        } catch (Exception e) {
            Log.e("ActVideoTrimmer", "setUpToolBar: ", e);
        }
    }

    private void initPlayer() {
        try {
            videoPlayer = new ExoPlayer.Builder(this).build();
            videoPlayer.setSeekParameters(SeekParameters.EXACT);
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
                long exactDurationMs = TrimmerUtils.getDurationMillis(ActVideoTrimmer.this, fileUri);
                totalDurationMs = Math.max(200L, exactDurationMs);
                hasRefinedDurationFromPlayer = false;

                runOnUiThread(() -> {
                    if (progressCircular != null) progressCircular.setVisibility(View.GONE);
                    imagePlayPause.setOnClickListener(v -> onVideoClicked());
                    View surfaceView = playerView.getVideoSurfaceView();
                    if (surfaceView != null) {
                        surfaceView.setOnClickListener(v -> onVideoClicked());
                    }
                    playerView.setOnClickListener(v -> onVideoClicked());

                    initTrimData();
                    buildMediaSource();
                    setupTwoTierTimeline();
                    setUpResChanger();
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

        windowStartMs = 0L;
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
                    if (!isUserScrubbing() && imagePlayPause != null) {
                        imagePlayPause.setVisibility(playWhenReady ? View.GONE : View.VISIBLE);
                    }
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_ENDED) {
                        if (!isUserScrubbing() && videoPlayer != null) {
                            // Ensure short/end-aligned videos reach 100% and seamlessly loop back to startMs
                            setOverviewSliderValueSafe(endMs);
                            if (rangeOverlay != null) {
                                rangeOverlay.setPlayheadPercent(rangeOverlay.getEndPercent());
                            }
                            isVideoEnded = false;
                            seekToMs(startMs);
                            videoPlayer.setPlayWhenReady(true);
                            if (imagePlayPause != null) {
                                imagePlayPause.setVisibility(View.GONE);
                            }
                        }
                    } else if (state == Player.STATE_READY) {
                        if (!hasRefinedDurationFromPlayer && videoPlayer != null) {
                            hasRefinedDurationFromPlayer = true;
                            long playerDur = videoPlayer.getDuration();
                            if (playerDur > 0L && playerDur != C.TIME_UNSET && Math.abs(playerDur - totalDurationMs) > 35L) {
                                refineTotalDurationFromPlayer(playerDur);
                            }
                        }
                        isVideoEnded = false;
                        if (!isUserScrubbing() && imagePlayPause != null && videoPlayer != null) {
                            imagePlayPause.setVisibility(videoPlayer.getPlayWhenReady() ? View.GONE : View.VISIBLE);
                        }
                        startProgress();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refineTotalDurationFromPlayer(long playerDur) {
        boolean wasPinnedToEnd = endMs >= (totalDurationMs - 60L);
        boolean wasDefaultOpeningRange = (startMs == 0L) && (wasPinnedToEnd || endMs >= MAX_CLIP_DURATION_MS);
        totalDurationMs = Math.max(200L, playerDur);
        if (wasDefaultOpeningRange) {
            startMs = 0L;
            endMs = Math.min(totalDurationMs, MAX_CLIP_DURATION_MS);
        } else {
            long minClip = getEffectiveMinClipMs();
            endMs = wasPinnedToEnd ? totalDurationMs : Math.min(endMs, totalDurationMs);
            startMs = Math.max(0L, Math.min(startMs, endMs - minClip));
            if (endMs - startMs > MAX_CLIP_DURATION_MS) {
                endMs = startMs + MAX_CLIP_DURATION_MS;
            }
        }
        setupTwoTierTimeline();
    }

    private long getViewportDurationMs() {
        return Math.max(200L, Math.min(totalDurationMs, MAX_CLIP_DURATION_MS));
    }

    private boolean isUserScrubbing() {
        return isUserScrubActive || isRangeDragging || isOverviewDragging || (isFilmstripScrolling && !isProgrammaticScroll);
    }

    private void beginUserScrub() {
        if (!isUserScrubActive) {
            isUserScrubActive = true;
            wasPlayingBeforeScrub = (videoPlayer != null && videoPlayer.getPlayWhenReady()) || isVideoEnded;
            if (videoPlayer != null) {
                videoPlayer.setPlayWhenReady(false);
            }
            if (imagePlayPause != null) {
                imagePlayPause.setVisibility(View.GONE);
            }
        }
    }

    private void liveScrubPreviewToMs(long targetMs, boolean isFinal, boolean fromOverview) {
        long clampedMs = Math.max(0L, Math.min(totalDurationMs, targetMs));
        if (txtCurrentDuration != null) {
            txtCurrentDuration.setText(TrimmerUtils.formatMillisPrecise(clampedMs));
        }
        if (rangeOverlay != null) {
            rangeOverlay.setPlayheadPercent(msToOverlayPercent(clampedMs));
        }
        if (!isOverviewDragging) {
            setOverviewSliderValueSafe(clampedMs);
        }

        if (videoPlayer == null) {
            if (isFinal) {
                isUserScrubActive = false;
            }
            return;
        }

        if (!isFinal) {
            long now = SystemClock.elapsedRealtime();
            boolean playerBusy = videoPlayer.getPlaybackState() == Player.STATE_BUFFERING;
            long minIntervalMs = playerBusy ? 95L : 33L;
            if (now - lastLiveScrubElapsedMs >= minIntervalMs) {
                if (seekHandler != null) {
                    seekHandler.removeCallbacks(pendingScrubRunnable);
                }
                pendingScrubTargetMs = -1L;
                lastLiveScrubElapsedMs = now;
                videoPlayer.seekTo(clampedMs);
            } else if (seekHandler != null) {
                pendingScrubTargetMs = clampedMs;
                seekHandler.removeCallbacks(pendingScrubRunnable);
                seekHandler.postDelayed(pendingScrubRunnable, 40L);
            }
        } else {
            if (seekHandler != null) {
                seekHandler.removeCallbacks(pendingScrubRunnable);
            }
            pendingScrubTargetMs = -1L;
            isUserScrubActive = false;
            isVideoEnded = false;

            if (wasPlayingBeforeScrub) {
                long resumeMs = (fromOverview && clampedMs >= startMs && clampedMs < (endMs - 80L))
                        ? clampedMs
                        : startMs;
                videoPlayer.seekTo(Math.max(0L, resumeMs));
                videoPlayer.setPlayWhenReady(true);
                if (imagePlayPause != null) {
                    imagePlayPause.setVisibility(View.GONE);
                }
            } else {
                videoPlayer.seekTo(clampedMs);
                if (imagePlayPause != null) {
                    imagePlayPause.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void handleOverviewScrub(long scrubTimeMs, boolean isFinal) {
        long clampedTimeMs = Math.max(0L, Math.min(totalDurationMs, scrubTimeMs));
        long clipLen = Math.max(getEffectiveMinClipMs(), endMs - startMs);
        if (totalDurationMs > clipLen) {
            float fraction = (float) clampedTimeMs / (float) Math.max(1L, totalDurationMs);
            long maxStart = totalDurationMs - clipLen;
            startMs = Math.max(0L, Math.min(maxStart, Math.round(fraction * maxStart)));
            endMs = startMs + clipLen;
            syncTimelineViewsWithoutSeek(true, clampedTimeMs);
        }
        liveScrubPreviewToMs(clampedTimeMs, isFinal, true);
    }

    private void setupTwoTierTimeline() {
        final long viewportDurationMs = getViewportDurationMs();
        final long minClipMs = getEffectiveMinClipMs();

        // Setup Tier 1: Full-video scrubber spanning 0%..100% of totalDurationMs for ALL videos
        if (layoutOverviewSlider != null) {
            layoutOverviewSlider.setVisibility(View.VISIBLE);
        }
        if (txtTotalVideoDuration != null) {
            txtTotalVideoDuration.setText(TrimmerUtils.formatMillisPrecise(totalDurationMs));
        }
        if (sliderOverview != null) {
            float safeMax = (float) Math.max(1L, totalDurationMs);
            sliderOverview.clearOnSliderTouchListeners();
            sliderOverview.clearOnChangeListeners();
            sliderOverview.setValueFrom(0f);
            sliderOverview.setValue(0f);
            sliderOverview.setValueTo(safeMax);
            setOverviewSliderValueSafe(startMs);

            sliderOverview.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                @Override
                public void onStartTrackingTouch(@NonNull Slider slider) {
                    isOverviewDragging = true;
                    beginUserScrub();
                }

                @Override
                public void onStopTrackingTouch(@NonNull Slider slider) {
                    long targetMs = Math.round(slider.getValue());
                    isOverviewDragging = false;
                    handleOverviewScrub(targetMs, true);
                }
            });
            sliderOverview.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) {
                    if (!isOverviewDragging) {
                        beginUserScrub();
                    }
                    handleOverviewScrub(Math.round(value), !isOverviewDragging);
                }
            });
        }

        // Setup Tier 2: Zoomed scrollable filmstrip (RecyclerView)
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvFilmstrip.setLayoutManager(layoutManager);
        rvFilmstrip.clearOnScrollListeners();

        rvFilmstrip.post(() -> {
            int viewportWidthPx = rvFilmstrip.getWidth();
            if (viewportWidthPx <= 0) {
                viewportWidthPx = getResources().getDisplayMetrics().widthPixels - ViewUtil.dpToPx(84);
            }
            int totalFilmstripWidthPx = (int) Math.round(viewportWidthPx * ((double) totalDurationMs / (double) viewportDurationMs));
            int targetFrameWidthPx = ViewUtil.dpToPx(44);
            int frameCount = Math.max(8, Math.round((float) totalFilmstripWidthPx / (float) Math.max(1, targetFrameWidthPx)));

            FilmstripAdapter adapter = new FilmstripAdapter(this, fileUri, totalDurationMs, frameCount, totalFilmstripWidthPx);
            rvFilmstrip.setAdapter(adapter);
        });

        rvFilmstrip.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (isProgrammaticScroll) return;
                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    if (!isFilmstripScrolling) {
                        isFilmstripScrolling = true;
                        beginUserScrub();
                    }
                } else if (isFilmstripScrolling) {
                    isFilmstripScrolling = false;
                    liveScrubPreviewToMs(startMs, true, false);
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (isProgrammaticScroll || !isFilmstripScrolling) return;
                long vpDur = getViewportDurationMs();
                long minClip = getEffectiveMinClipMs();
                int offset = recyclerView.computeHorizontalScrollOffset();
                int range = recyclerView.computeHorizontalScrollRange() - recyclerView.computeHorizontalScrollExtent();
                if (range > 0 && totalDurationMs > vpDur) {
                    float scrollFraction = Math.max(0f, Math.min(1f, (float) offset / (float) range));
                    long maxWindowStart = totalDurationMs - vpDur;
                    windowStartMs = Math.round(scrollFraction * maxWindowStart);

                    float sPct = rangeOverlay != null ? rangeOverlay.getStartPercent() : 0f;
                    float ePct = rangeOverlay != null ? rangeOverlay.getEndPercent() : 1f;
                    startMs = windowStartMs + Math.round(sPct * vpDur);
                    endMs = windowStartMs + Math.round(ePct * vpDur);
                    if (endMs > totalDurationMs) {
                        endMs = totalDurationMs;
                    }
                    if (endMs - startMs < minClip) {
                        startMs = Math.max(0L, endMs - minClip);
                    }

                    updateTimeLabels(startMs);
                    liveScrubPreviewToMs(startMs, false, false);
                }
            }
        });

        // Setup TrimmerRangeOverlayView
        float minSpan = Math.min(1f, (float) minClipMs / (float) viewportDurationMs);
        rangeOverlay.setAllowCenterDrag(totalDurationMs <= MAX_CLIP_DURATION_MS);
        rangeOverlay.setRange(0f, 1f, minSpan, 1.0f);
        rangeOverlay.setOnTrimRangeListener((startPercent, endPercent, dragMode, isFinal) -> {
            if (!isFinal && !isRangeDragging) {
                isRangeDragging = true;
                beginUserScrub();
            }

            long vpDuration = getViewportDurationMs();
            long minClip = getEffectiveMinClipMs();
            long newStart = windowStartMs + Math.round(startPercent * vpDuration);
            long newEnd = windowStartMs + Math.round(endPercent * vpDuration);

            newStart = Math.max(0L, Math.min(newStart, totalDurationMs - minClip));
            newEnd = Math.max(newStart + minClip, Math.min(newEnd, totalDurationMs));
            if (newEnd - newStart > MAX_CLIP_DURATION_MS) {
                if (dragMode == TrimmerRangeOverlayView.DRAG_LEFT) {
                    newStart = Math.max(0L, newEnd - MAX_CLIP_DURATION_MS);
                } else {
                    newEnd = Math.min(totalDurationMs, newStart + MAX_CLIP_DURATION_MS);
                }
            }

            startMs = newStart;
            endMs = newEnd;

            long previewMs = (dragMode == TrimmerRangeOverlayView.DRAG_RIGHT) ? endMs : startMs;
            updateTimeLabels(previewMs);

            if (isFinal) {
                isRangeDragging = false;
                liveScrubPreviewToMs(previewMs, true, false);
            } else {
                liveScrubPreviewToMs(previewMs, false, false);
            }
        });

        syncAllComponentsFromMs(true);
    }

    private void syncTimelineViewsWithoutSeek(boolean scrollFilmstrip, long displayPlayheadMs) {
        long viewportDurationMs = getViewportDurationMs();
        long maxWindowStartMs = Math.max(0L, totalDurationMs - viewportDurationMs);

        if (startMs < windowStartMs || endMs > (windowStartMs + viewportDurationMs)) {
            windowStartMs = Math.max(0L, Math.min(startMs, maxWindowStartMs));
        }

        if (scrollFilmstrip && rvFilmstrip != null && maxWindowStartMs > 0L) {
            int range = rvFilmstrip.computeHorizontalScrollRange() - rvFilmstrip.computeHorizontalScrollExtent();
            if (range > 0) {
                float ratio = (float) windowStartMs / (float) maxWindowStartMs;
                int targetScrollX = Math.round(ratio * range);
                int deltaX = targetScrollX - rvFilmstrip.computeHorizontalScrollOffset();
                if (deltaX != 0) {
                    isProgrammaticScroll = true;
                    rvFilmstrip.scrollBy(deltaX, 0);
                    isProgrammaticScroll = false;
                }
            }
        }

        if (rangeOverlay != null) {
            float startPct = (float) (startMs - windowStartMs) / (float) viewportDurationMs;
            float endPct = (float) (endMs - windowStartMs) / (float) viewportDurationMs;
            float minSpan = Math.min(1f, (float) getEffectiveMinClipMs() / (float) viewportDurationMs);
            rangeOverlay.setRange(startPct, endPct, minSpan, 1f);
        }

        updateTimeLabels(displayPlayheadMs);
    }

    private void syncAllComponentsFromMs(boolean scrollFilmstrip) {
        syncTimelineViewsWithoutSeek(scrollFilmstrip, startMs);
        setOverviewSliderValueSafe(startMs);
        seekToMs(startMs);
    }

    private void updateTimeLabels(long currentDisplayMs) {
        if (txtStartDuration != null) {
            txtStartDuration.setText(TrimmerUtils.formatMillisPrecise(startMs));
        }
        if (txtEndDuration != null) {
            txtEndDuration.setText(TrimmerUtils.formatMillisPrecise(endMs));
        }
        if (txtClipDuration != null) {
            float clipSec = (endMs - startMs) / 1000f;
            txtClipDuration.setText(String.format(Locale.getDefault(), getString(R.string.txt_clip_duration_format), clipSec));
        }
        if (txtCurrentDuration != null) {
            txtCurrentDuration.setText(TrimmerUtils.formatMillisPrecise(currentDisplayMs));
        }
        updateQuickDurationChipsState();
    }

    private void setOverviewSliderValueSafe(long ms) {
        if (sliderOverview != null && !isOverviewDragging) {
            float from = sliderOverview.getValueFrom();
            float to = sliderOverview.getValueTo();
            if (to > from) {
                float clamped = Math.max(from, Math.min(to, (float) ms));
                sliderOverview.setValue(clamped);
            }
        }
    }

    private float msToOverlayPercent(long currentMs) {
        long currentClipDuration = Math.max(1L, endMs - startMs);
        float overlayStart = rangeOverlay != null ? rangeOverlay.getStartPercent() : 0f;
        float overlayEnd = rangeOverlay != null ? rangeOverlay.getEndPercent() : 1f;
        if (currentMs <= startMs) return overlayStart;
        if (currentMs >= endMs) return overlayEnd;
        float fraction = (float) (currentMs - startMs) / (float) currentClipDuration;
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
            subtitle.setText(String.format("00:00 — %s", TrimmerUtils.formatMillisPrecise(totalDurationMs)));

            final long currentValMs = isStart ? startMs : endMs;
            input.setText(TrimmerUtils.formatMillisPrecise(currentValMs));
            input.setSelection(input.getText().length());

            androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setView(dialogView)
                    .create();

            // Nudge buttons inside dialog
            int surfaceHigh = resolveThemeAttrColor(com.google.android.material.R.attr.colorSurfaceContainerHigh);
            int onSurface = resolveThemeAttrColor(com.google.android.material.R.attr.colorOnSurface);
            int onPrimary = ColorUtils.calculateLuminance(primaryColor) > 0.5 ? Color.parseColor("#1C1B1F") : Color.WHITE;

            int[] nudgeIds = new int[]{
                    R.id.btn_nudge_minus_1s,
                    R.id.btn_nudge_minus_half,
                    R.id.btn_nudge_plus_half,
                    R.id.btn_nudge_plus_1s
            };
            float[] nudgeDeltas = new float[]{-1.0f, -0.5f, +0.5f, +1.0f};
            for (int i = 0; i < nudgeIds.length; i++) {
                MaterialButton btnNudge = dialogView.findViewById(nudgeIds[i]);
                if (btnNudge != null) {
                    btnNudge.setBackgroundTintList(ColorStateList.valueOf(surfaceHigh));
                    btnNudge.setTextColor(onSurface);
                    final float delta = nudgeDeltas[i];
                    btnNudge.setOnClickListener(v -> adjustManualInput(input, delta));
                }
            }

            MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
            if (btnCancel != null) {
                btnCancel.setTextColor(primaryColor);
                btnCancel.setOnClickListener(v -> dialog.dismiss());
            }

            MaterialButton btnApply = dialogView.findViewById(R.id.btn_apply);
            if (btnApply != null) {
                btnApply.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
                btnApply.setTextColor(onPrimary);
                btnApply.setOnClickListener(v -> {
                    long parsedMs = parseTimeInputToMs(input.getText().toString(), currentValMs);
                    applyManualTimeSelection(isStart, parsedMs);
                    dialog.dismiss();
                });
            }

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
            input.setText(TrimmerUtils.formatMillisPrecise(newMs));
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
                double totalSec = 0.0;
                for (String part : parts) {
                    totalSec = totalSec * 60.0 + Double.parseDouble(part.trim());
                }
                return Math.round(totalSec * 1000.0);
            } else {
                return Math.round(Double.parseDouble(text) * 1000.0);
            }
        } catch (Exception e) {
            return fallbackMs;
        }
    }

    private void applyManualTimeSelection(boolean isStart, long targetMs) {
        long minClip = getEffectiveMinClipMs();
        if (isStart) {
            long newStart = Math.max(0L, Math.min(targetMs, totalDurationMs - minClip));
            long newEnd = endMs;
            if (newEnd < newStart + minClip) {
                newEnd = Math.min(totalDurationMs, newStart + minClip);
            }
            if (newEnd - newStart > MAX_CLIP_DURATION_MS) {
                newEnd = Math.min(totalDurationMs, newStart + MAX_CLIP_DURATION_MS);
            }
            startMs = newStart;
            endMs = newEnd;
        } else {
            long newEnd = Math.max(minClip, Math.min(targetMs, totalDurationMs));
            long newStart = startMs;
            if (newEnd < newStart + minClip) {
                newStart = Math.max(0L, newEnd - minClip);
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
            String sourcePath;
            if (fileUri != null) {
                String scheme = fileUri.getScheme();
                if ((scheme == null || "file".equalsIgnoreCase(scheme)) && fileUri.getPath() != null) {
                    sourcePath = fileUri.getPath();
                } else {
                    sourcePath = fileUri.toString();
                    intent.setData(fileUri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            } else {
                sourcePath = "";
            }
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
