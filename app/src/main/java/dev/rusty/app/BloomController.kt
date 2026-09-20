package dev.rusty.app

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView

/**
 * Drives the idle⇄active "bloom". Holds references to the shared views; the Activity
 * calls [apply] on a visual-state edge. Cancels any in-flight animation before starting
 * the next so rapid flips never stack.
 */
class BloomController(
    private val clock: TextView,
    private val idleViews: List<View>,    // date, status
    private val activeViews: List<View>,  // identitySuffix, albumArtCard, playingInfo (children animate transitively)
    private val mesh: AmbientMeshView,
    private val wash: View,
    private val scrim: View,
    /** The saver's flat scrim, living on this surface so the two scrims can morph. */
    private val scrimFlat: View,
) {
    private var current: VisualState? = null
    private val cornerScale = BloomGeometry.CORNER_SCALE
    // The clock lives in the shell's full-window, inset-padded chrome layer; derive the corner box
    // from it directly so the morph works wherever the clock is parented (no `root` handle needed).
    private val parent get() = clock.parent as View
    private val marginPx get() = dpToPx(BloomGeometry.CORNER_SIDE_MARGIN_DP)
    private val topMarginPx get() = dpToPx(BloomGeometry.CORNER_TOP_MARGIN_DP)

    /**
     * [holdWash] = true when the screensaver on the other side of this morph shows the same
     * blurred album-art wash: the wash then stays put and the two scrims trade places, so the
     * background does not change at all across the transition. See [showActive] and [showIdle].
     */
    fun apply(state: VisualState, animate: Boolean, holdWash: Boolean = false) {
        if (state == current) return
        current = state
        clock.post {
            if (state == VisualState.ACTIVE) showActive(animate, holdWash) else showIdle(animate, holdWash)
        }
    }

    /**
     * Play the morph to idle knowing a wash-sharing screensaver is about to fade in on top: the
     * clock grows back to the centre, the now-playing elements leave, and the now-playing gradient
     * hands off to the flat scrim the saver draws. The saver then lands on a dashboard that already
     * looks like it, so its own crossfade has nothing to show — the mirror of the exit bloom.
     */
    fun morphToIdleUnderScreensaver() = apply(VisualState.IDLE, animate = true, holdWash = true)

    /**
     * Snap to the IDLE layout with no animation and mark IDLE as current, so the next
     * animated apply(ACTIVE) replays the full bloom (the state==current guard won't skip it).
     * Used when returning to the dashboard from the screensaver: we reset under the still-opaque
     * overlay, then animate, so the morph plays in sync with the overlay crossfade.
     * Must be called on the main thread (it mutates Views synchronously, unlike apply()'s posted body).
     *
     * [showMesh] = false keeps the ambient mesh hidden through the reset (and thus the whole morph),
     * so returning from a mesh-less saver (OLED) doesn't flash the mesh's colors over the dark exit.
     */
    fun resetToIdleInstant(showMesh: Boolean = true, holdWash: Boolean = false) {
        current = VisualState.IDLE
        // With holdWash this is not the mesh-backed idle face but a stand-in for the saver
        // crossfading away above us: same blurred wash, same flat scrim, clock centred. The
        // animated apply(ACTIVE) that follows then morphs out of exactly what the eye already sees.
        showIdle(animate = false, holdWash = holdWash)
        if (!showMesh) {
            // showIdle() queued a duration-0 alpha→1 on the mesh that would otherwise apply next
            // frame and clobber our 0 — cancel it so the mesh stays hidden through the whole morph
            // (returning from the mesh-less OLED saver must not flash the mesh's colors).
            mesh.animate().cancel()
            mesh.stop()
            mesh.alpha = 0f
        }
    }

    /**
     * Host visibility hooks. The mesh should drift only when it's actually visible — i.e.
     * on-screen AND idle — so the host calls these from onStart/onStop instead of poking the
     * mesh directly (which would restart it even in the playing state, where it's hidden).
     */
    fun onVisible() { if (current == VisualState.IDLE) mesh.start() else mesh.stop() }
    fun onHidden() { mesh.stop() }

    private fun showActive(animate: Boolean, holdWash: Boolean = false) {
        val (tx, ty) = clockCornerTranslation()
        val dur = if (animate) ACTIVE_MS else 0L

        // Freeze the mesh immediately (it's about to be hidden), then fade it out — no point
        // burning CPU/GPU redrawing an invisible mesh behind the wash on an always-on screen.
        mesh.stop()
        mesh.animate().alpha(0f).setDuration(dur).start()
        if (holdWash) {
            // Blooming out of a saver that shows this same blurred wash: it is already on screen,
            // so don't fade it in — that would dim the shared image for the length of the fade,
            // a seam in a background that is supposed to never change. The flat scrim we inherited
            // from the saver retires over the whole morph while the gradient takes its place, so
            // the darkness changes shape in step with the clock instead of blinking over.
            wash.animate().cancel()
            wash.alpha = 1f
            scrimFlat.animate().alpha(0f).setDuration(dur).start()
        } else {
            wash.alpha = if (animate) 0f else 1f
            wash.animate().alpha(1f).setDuration(if (animate) WASH_MS else 0L).start()
            scrimFlat.animate().cancel()
            scrimFlat.alpha = 0f
        }
        scrim.alpha = if (animate) 0f else 1f
        scrim.animate().alpha(1f).setDuration(dur).start()

        clock.animate().translationX(tx).translationY(ty).scaleX(cornerScale).scaleY(cornerScale)
            .setDuration(dur).setInterpolator(DecelerateInterpolator()).start()

        idleViews.forEach { it.animate().alpha(0f).setDuration(if (animate) 300L else 0L).start() }

        activeViews.forEachIndexed { i, v ->
            v.visibility = View.VISIBLE
            v.alpha = if (animate) 0f else 1f
            v.translationY = if (animate) dpToPx(16f) else 0f
            val delay = if (animate) 220L + i * 130L else 0L
            v.animate().alpha(1f).translationY(0f).setStartDelay(delay)
                .setDuration(if (animate) 520L else 0L).start()
        }
    }

    private fun showIdle(animate: Boolean, holdWash: Boolean = false) {
        val dur = if (animate) IDLE_MS else 0L
        if (holdWash) {
            // The other side of this morph is a saver drawing this same wash, so the background
            // must not move: hold the wash, keep the mesh out of it entirely, and let the flat
            // scrim take over from the gradient as it retires.
            mesh.stop()
            mesh.animate().cancel()
            mesh.alpha = 0f
            wash.animate().cancel()
            wash.alpha = 1f
            scrimFlat.animate().alpha(1f).setDuration(dur).start()
        } else {
            mesh.start() // resume drifting as the mesh fades back in
            mesh.animate().alpha(1f).setDuration(dur).start()
            wash.animate().alpha(0f).setDuration(dur).start()
            scrimFlat.animate().cancel()
            scrimFlat.alpha = 0f
        }
        scrim.animate().alpha(0f).setDuration(dur).start()
        clock.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(dur).setInterpolator(DecelerateInterpolator()).start()
        idleViews.forEach { it.visibility = View.VISIBLE }
        if (holdWash) {
            // The saver on the other side of this morph draws its own date and status, and it
            // positions that block below its own clock while we place ours by a parent bias — so
            // the two copies sit roughly a hundred pixels apart. Fading ours in would show the
            // text in one place and then hand over to the saver's in another, which is the jump
            // that read as the lines popping into position. Leave it to the saver.
            idleViews.forEach { it.animate().cancel(); it.alpha = 0f }
        } else {
            idleViews.forEach { it.animate().alpha(1f).setDuration(dur).start() }
        }
        // The mirror of the entrance in showActive(): the same 16dp of travel, the same stagger, in
        // reverse order so the row nearest the bottom leaves first. 200ms was fine when this only
        // ever ran under a dark exit, but here it is the animation being watched.
        val exitDur = if (animate) 340L else 0L
        activeViews.asReversed().forEachIndexed { i, v ->
            val delay = if (animate) i * 70L else 0L
            v.animate().alpha(0f).translationY(dpToPx(16f)).setStartDelay(delay).setDuration(exitDur)
                .withEndAction { v.visibility = View.GONE; v.translationY = 0f }.start()
        }
    }

    /**
     * Parks the centered clock in the top-right corner of the content box. Settings/info now
     * live bottom-right, so the clock no longer has to dodge them — it targets the padded
     * content edge directly (paddings keep it inside the system-bar insets).
     */
    private fun clockCornerTranslation(): Pair<Float, Float> =
        BloomGeometry.cornerTranslation(
            parentWidth = parent.width,
            parentPaddingRight = parent.paddingRight,
            parentPaddingTop = parent.paddingTop,
            // Use the layout edges (translation-free), NOT clock.x/y. The shell clock now persists
            // across feature switches, so it can carry a leftover park/active transform when this
            // runs; the corner translation is absolute (set, not added), so feeding it the already-
            // translated center would land the clock short of the corner. left/top are the clean
            // baseline, and equal clock.x/y in the common translationX==0 case.
            clockX = clock.left.toFloat(),
            clockY = clock.top.toFloat(),
            clockWidth = clock.width,
            clockHeight = clock.height,
            cornerScale = cornerScale,
            marginPx = marginPx,
            topMarginPx = topMarginPx,
        )

    private fun dpToPx(dp: Float): Float = dp * clock.resources.displayMetrics.density

    companion object {
        /**
         * How long the idle→active morph runs. Public because the screensaver's exit has to match
         * it: the saver fades its own scrim out across exactly this span so the darkness changes
         * in step with the clock's flight to the corner.
         */
        const val ACTIVE_MS = 900L

        /** The wash trails the morph slightly when it has to fade in at all. */
        private const val WASH_MS = 1000L

        /**
         * Active→idle. Public for the same reason as [ACTIVE_MS]: a wash-sharing screensaver lands
         * its own crossfade at the end of this morph, so it has to know how long it runs.
         */
        const val IDLE_MS = 700L
    }
}
