package com.posfingerprint.app

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.RenderEffect
import android.graphics.Shader
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/** Home: this phone's status, its POS terminals, fingerprints and recent activity. */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var root: View
    private lateinit var tvGreeting: TextView
    private lateinit var dotStatus: View
    private lateinit var tvPill: TextView
    private lateinit var tvHeroTitle: TextView
    private lateinit var tvHeroSubtitle: TextView
    private lateinit var tvHeroNote: TextView
    private lateinit var statsRow: View
    private lateinit var btnSetup: MaterialButton
    private lateinit var tvStatPos: TextView
    private lateinit var tvStatProfiles: TextView
    private lateinit var tvStatToday: TextView
    private lateinit var tvCountPos: TextView
    private lateinit var tvCountProfiles: TextView
    private lateinit var listPos: LinearLayout
    private lateinit var listProfiles: LinearLayout
    private lateinit var listActivity: LinearLayout
    private lateinit var cardActivity: View
    private lateinit var emptyPos: View
    private lateinit var emptyProfiles: View
    private lateinit var emptyActivity: View
    private lateinit var sectionWeb: View
    private lateinit var listWeb: LinearLayout
    private lateinit var heroGlow: View
    private lateinit var heroGlowLight: View
    private lateinit var heroRidges: ImageView
    private lateinit var heroRidgesBlur: ImageView
    private var breathing: AnimatorSet? = null
    private var glowColorRes = 0

    private var shownRevision = -1
    private var shownConnected: Boolean? = null

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refreshStatus()
            if (prefs.revision != shownRevision || prefs.connected != shownConnected) rebuildLists()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        Security.protect(this)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        root = findViewById(R.id.root)
        tvGreeting = findViewById(R.id.tvGreeting)
        dotStatus = findViewById(R.id.dotStatus)
        tvPill = findViewById(R.id.tvPill)
        tvHeroTitle = findViewById(R.id.tvHeroTitle)
        tvHeroSubtitle = findViewById(R.id.tvHeroSubtitle)
        tvHeroNote = findViewById(R.id.tvHeroNote)
        statsRow = findViewById(R.id.statsRow)
        btnSetup = findViewById(R.id.btnSetup)
        tvStatPos = findViewById(R.id.tvStatPos)
        tvStatProfiles = findViewById(R.id.tvStatProfiles)
        tvStatToday = findViewById(R.id.tvStatToday)
        tvCountPos = findViewById(R.id.tvCountPos)
        tvCountProfiles = findViewById(R.id.tvCountProfiles)
        listPos = findViewById(R.id.listPos)
        listProfiles = findViewById(R.id.listProfiles)
        listActivity = findViewById(R.id.listActivity)
        cardActivity = findViewById(R.id.cardActivity)
        emptyPos = findViewById(R.id.emptyPos)
        emptyProfiles = findViewById(R.id.emptyProfiles)
        emptyActivity = findViewById(R.id.emptyActivity)

        sectionWeb = findViewById(R.id.sectionWeb)
        listWeb = findViewById(R.id.listWeb)
        heroGlow = findViewById(R.id.heroGlow)
        heroGlowLight = findViewById(R.id.heroGlowLight)
        heroRidges = findViewById(R.id.heroRidges)
        heroRidgesBlur = findViewById(R.id.heroRidgesBlur)

        findViewById<View>(R.id.hero).clipToOutline = true
        setupGlow()
        smallIcon(findViewById(R.id.tvStatPosLabel), R.drawable.ic_store)
        smallIcon(findViewById(R.id.tvStatProfilesLabel), R.drawable.ic_fingerprint)
        smallIcon(findViewById(R.id.tvStatTodayLabel), R.drawable.ic_check_circle)

        val openSettings = View.OnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.btnSettings).setOnClickListener(openSettings)
        btnSetup.setOnClickListener(openSettings)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (prefs.connected) startPolling()
    }

    override fun onResume() {
        super.onResume()
        tvGreeting.text = Ui.greeting()
        rebuildLists()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        stopBreathing()
        super.onPause()
    }

    private fun startPolling() {
        ContextCompat.startForegroundService(this, Intent(this, PollService::class.java))
    }

    // ---------------------------------------------------------------- hero

    private fun setPill(text: String, dotColor: Int) {
        tvPill.text = text
        Ui.tint(dotStatus, getColor(dotColor))
    }

    private fun refreshStatus() {
        if (!prefs.connected) {
            setGlow(R.color.glow_idle, false)
            setPill("Not connected", R.color.dot_idle)
            tvHeroTitle.text = "Link this phone to Odoo"
            tvHeroSubtitle.text = "A one-time setup. After that, POS login requests arrive here automatically."
            tvHeroNote.isVisible = false
            statsRow.isVisible = false
            btnSetup.isVisible = true
            return
        }
        statsRow.isVisible = true
        btnSetup.isVisible = false
        tvHeroTitle.text = prefs.deviceName.ifEmpty { "This phone" }
        tvHeroSubtitle.text = if (prefs.database.isEmpty()) prefs.host else "${prefs.host}, database ${prefs.database}"
        when {
            RequestState.online -> {
                setGlow(R.color.glow_online, true)
                setPill("Online, waiting for requests", R.color.dot_online)
                tvHeroNote.isVisible = false
            }
            RequestState.lastError.isNotEmpty() -> {
                setGlow(R.color.glow_offline, false)
                setPill("Offline", R.color.dot_offline)
                tvHeroNote.text = RequestState.lastError
                tvHeroNote.isVisible = true
            }
            else -> {
                setGlow(R.color.glow_idle, false)
                setPill("Connecting…", R.color.dot_idle)
                tvHeroNote.isVisible = false
            }
        }
    }

    // ---------------------------------------------------------------- lists

    private fun rebuildLists() {
        shownRevision = prefs.revision
        shownConnected = prefs.connected
        val inflater = LayoutInflater.from(this)
        val terminals = prefs.terminals()
        val profiles = prefs.profiles()
        val events = prefs.events(8)

        tvStatPos.text = terminals.size.toString()
        tvStatProfiles.text = profiles.size.toString()
        tvStatToday.text = prefs.approvalsToday().toString()
        tvCountPos.text = terminals.size.toString()
        tvCountProfiles.text = profiles.size.toString()

        // Odoo sign-ins
        val web = prefs.webStats()
        val hasUserProfile = profiles.any { it.kind == Prefs.KIND_USER }
        sectionWeb.isVisible = web.count > 0 || hasUserProfile
        listWeb.removeAllViews()
        if (sectionWeb.isVisible) listWeb.addView(webView(inflater, web))

        // POS terminals
        listPos.removeAllViews()
        terminals.forEach { listPos.addView(terminalView(inflater, it)) }
        emptyPos.isVisible = terminals.isEmpty()
        setEmpty(
            emptyPos, R.drawable.ic_store, "No POS terminals yet",
            "They appear here after the first fingerprint login on a POS."
        )

        // Fingerprints
        listProfiles.removeAllViews()
        profiles.forEach { listProfiles.addView(profileView(inflater, it)) }
        emptyProfiles.isVisible = profiles.isEmpty()
        if (prefs.connected) {
            setEmpty(
                emptyProfiles, R.drawable.ic_fingerprint, "No fingerprints yet",
                "In Odoo open the employee, go to the Fingerprint tab and pick this phone."
            )
        } else {
            setEmpty(
                emptyProfiles, R.drawable.ic_link, "Connect first",
                "Link this phone to Odoo in Settings, then register fingerprints."
            )
        }

        // Activity
        listActivity.removeAllViews()
        events.forEachIndexed { i, e ->
            if (i > 0) listActivity.addView(divider())
            listActivity.addView(eventView(inflater, e))
        }
        cardActivity.isVisible = events.isNotEmpty()
        emptyActivity.isVisible = events.isEmpty()
        setEmpty(emptyActivity, R.drawable.ic_history, "Nothing yet", "Approvals and registrations will be listed here.")
    }

    private fun setEmpty(view: View, icon: Int, title: String, subtitle: String) {
        view.findViewById<ImageView>(R.id.emptyIcon).setImageResource(icon)
        view.findViewById<TextView>(R.id.emptyTitle).text = title
        view.findViewById<TextView>(R.id.emptySubtitle).text = subtitle
    }

    private fun terminalView(inflater: LayoutInflater, t: Prefs.Terminal): View {
        val v = inflater.inflate(R.layout.item_pos, listPos, false)
        v.findViewById<TextView>(R.id.tvPosName).text = t.name
        v.findViewById<TextView>(R.id.tvPosMeta).text =
            if (t.lastEmployee.isEmpty()) "Last login ${Ui.ago(t.lastUsed)}"
            else "${t.lastEmployee}, ${Ui.ago(t.lastUsed)}"
        v.findViewById<TextView>(R.id.tvPosCount).text = t.logins.toString()
        v.findViewById<TextView>(R.id.tvPosCountLabel).text = if (t.logins == 1) "login" else "logins"
        return v
    }

    private fun profileView(inflater: LayoutInflater, p: Prefs.Profile): View {
        val v = inflater.inflate(R.layout.item_profile, listProfiles, false)
        val display = p.name.ifEmpty { "Fingerprint …" + p.alias.takeLast(4) }

        v.findViewById<TextView>(R.id.tvAvatar).apply {
            text = Ui.initials(p.name)
            Ui.tint(this, Ui.avatarColor(p.alias))
        }
        v.findViewById<TextView>(R.id.tvName).text = display
        v.findViewById<TextView>(R.id.tvMeta).text = when {
            p.lastUsed > 0 -> "Last login ${Ui.ago(p.lastUsed)}, ${p.uses} in total"
            p.createdAt > 0 -> "Registered ${Ui.ago(p.createdAt)}, not used yet"
            else -> "Ready on this phone"
        }

        v.findViewById<TextView>(R.id.tvKindBadge).apply {
            when (p.kind) {
                Prefs.KIND_USER -> {
                    text = "Odoo user"
                    setTextColor(getColor(R.color.on_primary_container))
                    Ui.tint(this, getColor(R.color.primary_container))
                    isVisible = true
                }
                Prefs.KIND_EMPLOYEE -> {
                    text = "POS cashier"
                    setTextColor(getColor(R.color.success))
                    Ui.tint(this, getColor(R.color.success_container))
                    isVisible = true
                }
                else -> isVisible = false
            }
        }

        val tags = v.findViewById<LinearLayout>(R.id.posTags)
        v.findViewById<View>(R.id.posTagsScroll).isVisible = p.terminals.isNotEmpty() || p.webUses > 0
        if (p.webUses > 0) tags.addView(tag("Odoo sign-in", odoo = true))
        p.terminals.forEach { tags.addView(tag(it)) }

        v.findViewById<View>(R.id.btnMore).setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                menu.add("Remove from this phone")
                setOnMenuItemClickListener {
                    confirmRemove(p.alias, display)
                    true
                }
                show()
            }
        }
        return v
    }

    private fun tag(text: String, odoo: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        setTextAppearance(R.style.Text_Badge)
        setTextColor(getColor(if (odoo) R.color.white else R.color.on_primary_container))
        setBackgroundResource(R.drawable.bg_pill)
        Ui.tint(this, getColor(if (odoo) R.color.signal else R.color.primary_container))
        val h = Ui.dp(this, 10)
        val vpad = Ui.dp(this, 4)
        val gap = Ui.dp(this, 6)
        setPadding(h, vpad, h, vpad)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = gap }
    }

    private fun eventView(inflater: LayoutInflater, e: Prefs.Event): View {
        val v = inflater.inflate(R.layout.item_activity, listActivity, false)
        val (icon, fg, bg) = when (e.type) {
            Prefs.EV_LOGIN -> Triple(R.drawable.ic_check, R.color.success, R.color.success_container)
            Prefs.EV_WEB_LOGIN -> Triple(R.drawable.ic_web, R.color.on_primary_container, R.color.primary_container)
            Prefs.EV_REGISTER -> Triple(R.drawable.ic_fingerprint, R.color.on_primary_container, R.color.primary_container)
            Prefs.EV_REJECT -> Triple(R.drawable.ic_close, R.color.error, R.color.error_container)
            Prefs.EV_FAILED -> Triple(R.drawable.ic_error, R.color.warning, R.color.warning_container)
            else -> Triple(R.drawable.ic_delete, R.color.text_secondary, R.color.surface_alt)
        }
        Ui.tint(v.findViewById(R.id.actIconBg), getColor(bg))
        v.findViewById<ImageView>(R.id.actIcon).apply {
            setImageResource(icon)
            setColorFilter(getColor(fg))
        }
        v.findViewById<TextView>(R.id.actTitle).text = e.title
        v.findViewById<TextView>(R.id.actDetail).text = e.detail
        v.findViewById<TextView>(R.id.actTime).text = Ui.ago(e.time)
        return v
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(getColor(R.color.divider))
        val inset = Ui.dp(this, 64)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)).apply {
            marginStart = inset
        }
    }

    private fun webView(inflater: LayoutInflater, web: Prefs.WebStats): View {
        val v = inflater.inflate(R.layout.item_pos, listWeb, false)
        v.findViewById<ImageView>(R.id.posIcon).apply {
            setImageResource(R.drawable.ic_web)
            imageTintList = ColorStateList.valueOf(getColor(R.color.white))
        }
        Ui.tint(v.findViewById(R.id.posIconBg), getColor(R.color.signal))
        v.findViewById<TextView>(R.id.tvPosName).text = "Odoo web login"
        v.findViewById<TextView>(R.id.tvPosMeta).text =
            if (web.count == 0) "Ready. Sign in on the Odoo login page."
            else "${web.lastUser.ifEmpty { "Last sign-in" }}, ${Ui.ago(web.lastAt)}"
        v.findViewById<TextView>(R.id.tvPosCount).text = web.count.toString()
        v.findViewById<TextView>(R.id.tvPosCountLabel).text = if (web.count == 1) "sign-in" else "sign-ins"
        return v
    }

    /** A 14dp icon in front of a hero stat label. */
    private fun smallIcon(tv: TextView, res: Int) {
        val d = getDrawable(res)?.mutate() ?: return
        val size = Ui.dp(tv, 14)
        d.setBounds(0, 0, size, size)
        d.setTint(getColor(R.color.hero_text_dim))
        tv.setCompoundDrawablesRelative(d, null, null, null)
        tv.compoundDrawablePadding = Ui.dp(tv, 5)
    }

    // ---------------------------------------------------------------- hero glow

    private fun setupGlow() {
        if (Build.VERSION.SDK_INT >= 31) {
            heroRidgesBlur.setRenderEffect(RenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.DECAL))
        } else {
            heroRidgesBlur.isVisible = false // no real blur before Android 12: the soft light still glows
        }
    }

    /** Colours the fingerprint glow by status and breathes slowly while online. */
    private fun setGlow(colorRes: Int, alive: Boolean) {
        if (colorRes != glowColorRes) {
            glowColorRes = colorRes
            val tint = ColorStateList.valueOf(getColor(colorRes))
            heroRidges.imageTintList = tint
            heroRidgesBlur.imageTintList = tint
            heroGlowLight.backgroundTintList = tint
        }
        if (alive) startBreathing() else stopBreathing()
    }

    private fun startBreathing() {
        if (breathing != null) return
        if (!ValueAnimator.areAnimatorsEnabled()) {
            heroGlow.alpha = 1f // the user turned animations off: keep it still
            return
        }
        fun pulse(target: View, prop: String, from: Float, to: Float) =
            ObjectAnimator.ofFloat(target, prop, from, to).apply {
                duration = 2600
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            }
        breathing = AnimatorSet().apply {
            playTogether(
                pulse(heroGlowLight, "alpha", 0.55f, 1f),
                pulse(heroRidgesBlur, "alpha", 0.45f, 0.95f),
                pulse(heroGlow, "scaleX", 0.98f, 1.03f),
                pulse(heroGlow, "scaleY", 0.98f, 1.03f),
            )
            start()
        }
    }

    private fun stopBreathing() {
        breathing?.cancel()
        breathing = null
        heroGlowLight.alpha = 0.8f
        heroRidgesBlur.alpha = 0.7f
        heroGlow.scaleX = 1f
        heroGlow.scaleY = 1f
    }

    private fun confirmRemove(alias: String, name: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove $name?")
            .setMessage(
                "The fingerprint key is deleted from this phone, so it can no longer approve POS logins. " +
                    "To use it again, configure the fingerprint from the employee in Odoo."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                prefs.removeProfile(alias)
                rebuildLists()
                Snackbar.make(root, "$name removed from this phone", Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }
}
