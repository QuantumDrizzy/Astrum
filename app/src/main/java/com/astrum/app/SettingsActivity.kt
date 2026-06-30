package com.astrum.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.astrum.app.databinding.ActivitySettingsBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * User settings: pick the location source (auto GPS vs a manual fixed point) and optionally freeze
 * the time to plan a different night. Persists to AppPrefs; MainActivity re-reads on resume.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private val cal = Calendar.getInstance()
    private val fmt = SimpleDateFormat("EEE d MMM yyyy · HH:mm", Locale("es", "ES"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        // ── Load current prefs into the form ──
        if (AppPrefs.manualLocation) b.rbManual.isChecked = true else b.rbAuto.isChecked = true
        if (AppPrefs.manualLat != 0.0 || AppPrefs.manualLng != 0.0) {
            b.etLat.setText(AppPrefs.manualLat.toString())
            b.etLng.setText(AppPrefs.manualLng.toString())
        }
        b.etName.setText(AppPrefs.manualName)
        updateLocationInputs(AppPrefs.manualLocation)

        cal.timeInMillis = AppPrefs.frozenEpochMillis
        if (AppPrefs.frozenTime) b.rbTimeFrozen.isChecked = true else b.rbTimeReal.isChecked = true
        updateTimeInputs(AppPrefs.frozenTime)

        // ── Wiring ──
        b.rgLocation.setOnCheckedChangeListener { _, id -> updateLocationInputs(id == b.rbManual.id) }
        b.rgTime.setOnCheckedChangeListener { _, id -> updateTimeInputs(id == b.rbTimeFrozen.id) }
        b.btnPickDateTime.setOnClickListener { pickDateTime() }
        b.btnSave.setOnClickListener { save() }
    }

    private fun updateLocationInputs(manual: Boolean) {
        val v = if (manual) View.VISIBLE else View.GONE
        b.etLat.visibility = v
        b.etLng.visibility = v
        b.etName.visibility = v
    }

    private fun updateTimeInputs(frozen: Boolean) {
        val v = if (frozen) View.VISIBLE else View.GONE
        b.btnPickDateTime.visibility = v
        b.tvFrozenInfo.visibility = v
        b.tvFrozenInfo.text = fmt.format(Date(cal.timeInMillis))
    }

    private fun pickDateTime() {
        DatePickerDialog(this, { _, y, mo, d ->
            cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, mo); cal.set(Calendar.DAY_OF_MONTH, d)
            TimePickerDialog(this, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); cal.set(Calendar.SECOND, 0)
                b.tvFrozenInfo.text = fmt.format(Date(cal.timeInMillis))
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun save() {
        val manual = b.rbManual.isChecked
        if (manual) {
            val lat = b.etLat.text.toString().trim().toDoubleOrNull()
            val lng = b.etLng.text.toString().trim().toDoubleOrNull()
            if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
                Toast.makeText(this, "Coordenadas inválidas (lat -90..90, lng -180..180)", Toast.LENGTH_LONG).show()
                return
            }
            AppPrefs.manualLat = lat
            AppPrefs.manualLng = lng
            AppPrefs.manualName = b.etName.text.toString().trim()
        }
        AppPrefs.manualLocation = manual

        AppPrefs.frozenTime = b.rbTimeFrozen.isChecked
        if (b.rbTimeFrozen.isChecked) AppPrefs.frozenEpochMillis = cal.timeInMillis

        finish() // MainActivity re-reads prefs in onResume
    }
}
