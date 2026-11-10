package com.colheitagps.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.time.*
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

internal object WeatherData {
    const val SOURCE = "MET Norway · CC BY 4.0"
    const val USER_AGENT = "DMAGRO/1.3.0 (https://github.com/Diegomachadosl10/colheita-gps-oficial)"
    data class City(val id: String, val name: String, val state: String, val lat: Double, val lon: Double, val zone: String) {
        val label get() = "$name · $state"
        fun json() = JSONObject().put("id", id).put("name", name).put("state", state).put("lat", lat).put("lon", lon).put("zone", zone)
    }
    fun city(obj: JSONObject) = City(obj.getString("id"), obj.getString("name"), obj.getString("state"), obj.getDouble("lat"), obj.getDouble("lon"), obj.getString("zone"))
    fun cities(context: Context): List<City> {
        val all = JSONArray(context.assets.open("municipios.json").bufferedReader().use { it.readText() })
        return (0 until all.length()).map { city(all.getJSONObject(it)) }
    }
    fun selected(context: Context, team: String): City? = try {
        context.getSharedPreferences("tempo_$team", 0).getString("cidade", null)?.let { city(JSONObject(it)) }
    } catch (_: Exception) { null }
    fun select(context: Context, team: String, city: City) { context.getSharedPreferences("tempo_$team", 0).edit().putString("cidade", city.json().toString()).apply() }
    private fun cache(context: Context, city: City) = context.applicationContext.getSharedPreferences("previsao_${city.id}", 0)
    fun cached(context: Context, city: City): JSONObject? = try {
        cache(context, city).getString("json", null)?.let(::JSONObject)?.takeIf {
            (it.optJSONObject("properties")?.optJSONArray("timeseries")?.length() ?: 0)>0
        }
    } catch (_: Exception) { null }
    fun load(context: Context, city: City): JSONObject {
        val prefs = cache(context, city)
        val previous = cached(context, city)
        if (previous != null && prefs.getLong("ate", 0) > System.currentTimeMillis()) return previous
        val lat = String.format(Locale.US, "%.2f", city.lat); val lon = String.format(Locale.US, "%.2f", city.lon)
        val connection = URL("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$lat&lon=$lon").openConnection() as HttpsURLConnection
        connection.connectTimeout = 15000; connection.readTimeout = 15000; connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", USER_AGENT)
        prefs.getString("modified", null)?.let { connection.setRequestProperty("If-Modified-Since", it) }
        try {
            val result = when (connection.responseCode) {
                304 -> requireNotNull(previous)
                200 -> {
                    val text = connection.inputStream.use { stream ->
                        val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                        while(true) { val count=stream.read(buffer); if(count<0) break; require(output.size()+count<=2*1024*1024) { "Previsão acima do limite de leitura" }; output.write(buffer,0,count) }
                        String(output.toByteArray(), Charsets.UTF_8)
                    }
                    JSONObject(text).also { require(it.getJSONObject("properties").getJSONArray("timeseries").length() > 0) }
                }
                else -> throw java.io.IOException("Fonte meteorológica indisponível (${connection.responseCode})")
            }
            val until = maxOf(System.currentTimeMillis() + 15 * 60 * 1000, connection.expiration)
            prefs.edit().putString("json", result.toString()).putLong("ate", until)
                .putString("modified", connection.getHeaderField("Last-Modified")).apply()
            return result
        } finally { connection.disconnect() }
    }
    data class Hour(val time: Instant, val temperature: Double, val wind: Double, val humidity: Double, val symbol: String)
    fun hours(json: JSONObject): List<Hour> {
        val series = json.getJSONObject("properties").getJSONArray("timeseries")
        return (0 until series.length()).mapNotNull { i -> try {
            val item=series.getJSONObject(i); val data=item.getJSONObject("data"); val details=data.getJSONObject("instant").getJSONObject("details")
            val symbol = (data.optJSONObject("next_1_hours") ?: data.optJSONObject("next_6_hours") ?: data.optJSONObject("next_12_hours"))
                ?.optJSONObject("summary")?.optString("symbol_code").orEmpty()
            Hour(Instant.parse(item.getString("time")), details.getDouble("air_temperature"), details.optDouble("wind_speed", 0.0), details.optDouble("relative_humidity", 0.0), symbol)
        } catch (_: Exception) { null } }.sortedBy { it.time }
    }
    fun description(symbol: String): String = when {
        symbol.contains("thunder") -> "Trovoadas"
        symbol.contains("heavyrain") -> "Chuva forte"
        symbol.contains("rain") -> "Chuva prevista"
        symbol.contains("snow") || symbol.contains("sleet") -> "Precipitação de inverno"
        symbol.contains("fog") -> "Nevoeiro"
        symbol.contains("partlycloudy") -> "Parcialmente nublado"
        symbol.contains("cloudy") -> "Nublado"
        symbol.contains("fair") -> "Poucas nuvens"
        symbol.contains("clearsky") -> "Céu aberto"
        else -> "Consulte a previsão detalhada"
    }
    fun nearNow(json: JSONObject, now: Instant = Instant.now()) = hours(json).firstOrNull { !it.time.isBefore(now.minusSeconds(3600)) }
    fun degrees(value: Double) = String.format(Locale("pt", "BR"), "%.0f °C", value)
}
