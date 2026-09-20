package dev.rusty.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Locale
import java.util.TimeZone

/**
 * Full-bleed Canvas screensaver theme. Plays the current track's Canvas loop behind a subtle
 * clock/track overlay; when the track has no Canvas it falls back to the blurred album-art wash +
 * clock, so the saver still looks intentional. Independent of the now-playing toggle: selecting the
 * theme is the opt-in, so its controller is always enabled.
 */
class CanvasTheme(
    /** When true the Canvas player is never started; the album-art wash is the whole face. */
    private val artworkOnly: Boolean = false,
) : ScreensaverTheme {
    private lateinit var root: View
    private lateinit var wash: ImageView
    private lateinit var scrim: android.view.View
    private lateinit var canvasPlayer: CanvasPlayerView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var status: TextView
    private lateinit var chrome: View
    private lateinit var launcher: FeatureLauncher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: CanvasController? = null
    private var canvasActive = false
    private var loadedWashUrl: String? = null

    override fun createView(context: Context, parent: ViewGroup, host: ScreensaverHost): View {
        root = LayoutInflater.from(context).inflate(R.layout.screensaver_canvas, parent, false)
        wash = root.findViewById(R.id.ssCanvasWash)
        scrim = root.findViewById(R.id.ssCanvasScrim)
        canvasPlayer = root.findViewById(R.id.ssCanvasPlayer)
        canvasPlayer.setFill(true)
        clock = root.findViewById(R.id.ssClock)
        date = root.findViewById(R.id.ssDate)
        status = root.findViewById(R.id.ssStatus)
        chrome = root.findViewById(R.id.ssChrome)
        root.findViewById<ImageButton>(R.id.ssBtnSettings).setOnClickListener { host.openSettings() }
        root.findViewById<ImageButton>(R.id.ssBtnInfo).setOnClickListener { host.openInfo() }
        launcher = FeatureLauncher(
            toggle = root.findViewById(R.id.ssBtnLauncher),
            menu = root.findViewById<LinearLayout>(R.id.ssLauncherMenu),
            scrim = root.findViewById(R.id.ssLauncherScrim),
            activeTint = ContextCompat.getColor(context, R.color.accent_fallback),
            inactiveTint = ContextCompat.getColor(context, R.color.ink),
            itemLayoutRes = R.layout.view_launcher_item,
            minEntriesToShow = 2,
        ) { host.launcherEntries() }
        launcher.refresh()

        val store = RustyApp.from(context)
        controller = CanvasController(
            store = store,
            fetcher = CanvasRepository.shared,
            tokenProvider = androidSpotifyTokenProvider(context)::token,
            // Selecting the theme is the opt-in, so this is not the now-playing toggle. Under
            // ALBUM_ART the gate is permanently false: the controller never takes a reaction
            // key, so no Canvas is ever fetched and the wash below is all that shows.
            isEnabled = { !artworkOnly },
            scope = scope,
        ).also { it.addListener { state -> renderCanvas(state) } }
        return root
    }

    override fun bind(state: ReceiverDashboardState, is24Hour: Boolean) {
        val now = System.currentTimeMillis()
        val locale = root.resources.configuration.locales[0] ?: Locale.getDefault()
        val zone = TimeZone.getDefault()
        clock.text = ClockFormat.time(now, is24Hour, locale, zone)
        date.text = ClockFormat.date(now, locale, zone)
        status.text = state.idleStatus().first
        // Keep the blurred wash populated from the cover so the no-Canvas fallback looks intentional.
        loadWash(state.coverArtUrl)
    }

    private fun loadWash(url: String?) {
        if (url == loadedWashUrl) return
        loadedWashUrl = url
        if (url.isNullOrBlank()) { wash.setImageDrawable(null); return }
        // Blurred, not the raw cover. This used to load the full-resolution artwork straight
        // into a centerCrop ImageView, so the screensaver's centred clock, date and status sat
        // on sharp, arbitrarily-bright detail and could be unreadable. Coil runs the transform
        // off the main thread and caches the result.
        wash.load(url) {
            crossfade(true)
            // Needed to read pixels back for the scrim measurement below.
            allowHardware(false)
            transformations(BlurTransformation())
            listener(onSuccess = { _, result ->
                val blurred = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (blurred != null) {
                    // The DRAWABLE's alpha, not the view's: on the now-playing face BloomController
                    // owns view alpha as the show/hide channel, and keeping the same split here
                    // means strength and visibility never fight each other.
                    scrim.background?.mutate()?.alpha =
                        (ScrimStrength.forBackground(blurred) * 255f).toInt().coerceIn(0, 255)
                }
            })
        }
    }

    private fun renderCanvas(state: CanvasState) {
        when (state) {
            is CanvasState.Found -> {
                canvasPlayer.play(state.url)
                if (!canvasActive) {
                    canvasActive = true
                    canvasPlayer.visibility = View.VISIBLE
                    canvasPlayer.animate().alpha(1f).setDuration(300L).start()
                }
            }
            CanvasState.Loading, CanvasState.None -> {
                if (canvasActive) {
                    canvasActive = false
                    canvasPlayer.animate().alpha(0f).setDuration(300L).withEndAction {
                        canvasPlayer.visibility = View.GONE
                        canvasPlayer.clear()
                    }.start()
                }
            }
        }
    }

    /**
     * This face paints the blurred wash, its own scrim and grain — never an ambient mesh. It was
     * inheriting the default `true`, which told the exiting dashboard to keep its mesh at full
     * alpha under our fade: with the overlay gone in a quarter second and the dashboard's own wash
     * only a quarter faded in, what showed through the gap was the mesh's three drifting colours.
     */
    override val rendersAmbientMesh: Boolean get() = false

    /** The wash below is the same blurred cover the now-playing face uses. */
    override val sharesArtworkBackground: Boolean get() = true

    override fun onShown() { controller?.start() }

    // Stop resolving, but leave the player alone: the loop stays on screen for the exit crossfade
    // and onHidden() (teardown) releases the codec a moment later. This only prevents a NEW decoder
    // being opened during the fade.
    override fun onExitStarted() { controller?.stop() }

    override fun onHidden() {
        controller?.stop()
        canvasPlayer.animate().cancel()
        canvasPlayer.release()
        canvasActive = false
    }

    override fun setChromeVisible(visible: Boolean) {
        if (!visible) launcher.collapse()
        chrome.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun refreshLauncher() = launcher.refresh()
}
