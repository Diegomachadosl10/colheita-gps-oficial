package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WeatherActivity : Activity() {
    private lateinit var ui: AccessUi
    private var team = ""
    private var request = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); ui = AccessUi(this)
        team = intent.getStringExtra("equipe").orEmpty()
        if (team.substringBeforeLast('_') !in TeamPolicy.fronts || team.substringAfterLast('_') !in TeamPolicy.shifts) { finish(); return }
        render()
    }
    private fun chooseCity() {
        val field = EditText(this).apply { hint = "Nome do município"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Município da previsão").setView(field).setNegativeButton("Cancelar", null)
            .setPositiveButton("Buscar") { _, _ ->
                val query = AccessPolicy.normalized(field.text.toString())
                val found = if (query.length < 2) emptyList() else WeatherData.cities(this).filter { AccessPolicy.normalized(it.label).contains(query) }.take(40)
                if (found.isEmpty()) { AlertDialog.Builder(this).setMessage("Digite pelo menos duas letras e confira o município.").setPositiveButton("OK", null).show(); return@setPositiveButton }
                AlertDialog.Builder(this).setTitle("Escolha o município e estado").setItems(found.map { it.label }.toTypedArray()) { _, index ->
                    WeatherData.select(this, team, found[index]); render()
                }.show()
            }.show()
    }
    private fun render() {
        val token = ++request
        val city = WeatherData.selected(this, team)
        val body = ui.page("Previsão do tempo", "Município de referência para ${TeamPolicy.label(team)}. Esta escolha não altera sua frente ou turno.")
        ui.button(body, city?.let { "Trocar município: ${it.label}" } ?: "Escolher município") { chooseCity() }
        ui.add(body, ui.text("Fonte: ${WeatherData.SOURCE}\nTemperaturas, vento e condições são previsões do modelo, e não medições do campo."))
        ui.button(body, "Voltar", true) { finish() }
        if (city == null) return
        val forecast = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ui.add(body, forecast)
        val info = ui.text("Consultando previsão…"); ui.add(forecast, info)
        WeatherData.cached(this, city)?.let { show(forecast, city, it, true) }
        Thread {
            try {
                val json = WeatherData.load(applicationContext, city)
                runOnUiThread { if (!isDestroyed && token == request) show(forecast, city, json, false) }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isDestroyed && token == request) {
                        if (WeatherData.cached(this, city) == null) info.text = "Não foi possível consultar. Confira a internet e tente novamente."
                        else ui.add(forecast, ui.text("Sem atualização pela internet. Os dados acima estão guardados neste aparelho."))
                        ui.button(forecast, "Tentar novamente") { render() }
                    }
                }
            }
        }.start()
    }
    private fun show(body: LinearLayout, city: WeatherData.City, json: org.json.JSONObject, cached: Boolean) {
        body.removeAllViews()
        val current = WeatherData.nearNow(json)
        if (current == null) { ui.add(body, ui.text("Previsão guardada desatualizada. Aguarde uma nova consulta pela internet.")); return }
        ui.add(body, ui.text(WeatherData.degrees(current.temperature), 36f, ui.dark, true))
        ui.add(body, ui.text(WeatherData.description(current.symbol), 19f, ui.green, true))
        ui.add(body, ui.text(String.format(java.util.Locale("pt", "BR"), "Vento previsto: %.0f km/h · Umidade: %.0f%%", current.wind * 3.6, current.humidity)))
        val zone = ZoneId.of(city.zone)
        val byDay = WeatherData.hours(json).filter { !it.time.isBefore(java.time.Instant.now()) }.groupBy { it.time.atZone(zone).toLocalDate() }
        for ((date, values) in byDay.entries.take(5)) {
            ui.add(body, ui.text("${date.format(DateTimeFormatter.ofPattern("dd/MM"))} · ${WeatherData.degrees(values.minOf { it.temperature })} a ${WeatherData.degrees(values.maxOf { it.temperature })}", 17f, ui.dark, true))
            ui.add(body, ui.text(WeatherData.description(values[values.size / 2].symbol)), 2)
        }
        val updated = json.optJSONObject("properties")?.optJSONObject("meta")?.optString("updated_at").orEmpty().ifBlank { "não informada" }
        val formatted = try { java.time.Instant.parse(updated).atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) } catch (_: Exception) { updated }
        ui.add(body, ui.text("Modelo atualizado em $formatted${if (cached) " · dados guardados" else ""}.\nFaixas calculadas a partir dos horários disponíveis."))
        ui.button(body, "Fonte e licença CC BY 4.0", true) {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://api.met.no/doc/License")))
        }
    }
    companion object {
        fun homeCard(activity: Activity, team: String): View {
            val ui = DashboardUi(activity)
            return ui.panel { activity.startActivity(Intent(activity, WeatherActivity::class.java).putExtra("equipe", team)) }.apply {
                orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                addView(FieldIconView(activity, FieldIcon.WEATHER), LinearLayout.LayoutParams(ui.dp(38), ui.dp(38)))
                val city = WeatherData.selected(activity, team)
                val hour = city?.let { WeatherData.cached(activity, it)?.let { json -> WeatherData.nearNow(json) } }
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(ui.dp(13), 0, 0, 0)
                    addView(ui.text("Previsão do tempo", 16f, bold = true))
                    addView(ui.text(if (city == null) "Escolher município" else "${city.label} · ${hour?.let { WeatherData.degrees(it.temperature) } ?: "Consultar"}", 13f, ui.mint))
                    addView(ui.text("Fonte: MET Norway", 11f, ui.mint))
                }, LinearLayout.LayoutParams(0,-2,1f))
            }
        }
    }
}
