package com.colheitagps.app

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class WeatherDataTest {
    private fun forecast()=JSONObject("""{"properties":{"timeseries":[
      {"time":"2026-11-10T12:00:00Z","data":{"instant":{"details":{"air_temperature":25,"wind_speed":3,"relative_humidity":60}},"next_1_hours":{"summary":{"symbol_code":"partlycloudy_day"}}}},
      {"time":"2026-11-10T13:00:00Z","data":{"instant":{"details":{"air_temperature":26}},"next_6_hours":{"summary":{"symbol_code":"rain"}}}}
    ]}}""")
    @Test fun forecastUsesTheModelTimestampAndTranslatesItsConditionsWithoutInventingMeasurements() {
        val json=forecast(); val now=Instant.parse("2026-11-10T12:30:00Z")
        val reading=WeatherData.nearNow(json,now)!!
        assertEquals(25.0,reading.temperature,0.01); assertEquals("Parcialmente nublado",WeatherData.description(reading.symbol))
        assertEquals("Chuva prevista",WeatherData.description(WeatherData.hours(json).last().symbol))
        assertNull(WeatherData.nearNow(json,Instant.parse("2026-11-12T12:00:00Z")))
    }
    @Test fun endpointsRejectUntrustedOrCredentialBearingCloudflareAddresses() {
        assertTrue(AgroService.validEndpoint(AgroService.DEFAULT_ENDPOINT))
        assertTrue(AgroService.validEndpoint("https://dm-agro.usuario.workers.dev"))
        for(url in listOf("http://dm-agro.usuario.workers.dev","https://workers.dev.evil.invalid","https://dm-agro.usuario.workers.dev/other","https://key@dm-agro.usuario.workers.dev","https://dm-agro.usuario.workers.dev?token=abc")) assertFalse(url,AgroService.validEndpoint(url))
    }
}
