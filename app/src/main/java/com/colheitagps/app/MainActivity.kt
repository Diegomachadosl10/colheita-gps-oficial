package com.colheitagps.app

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.content.pm.PackageManager
import android.location.LocationManager
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
        }

        val titulo = TextView(this).apply {
            text = "COLHEITA GPS"
            textSize = 28f
            setTextColor(Color.rgb(20, 100, 45))
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            text = "GPS aguardando ativação"
            textSize = 18f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }

        val botao = Button(this).apply {
            text = "ATIVAR GPS"
            setOnClickListener { ativarGps() }
        }

        layout.addView(titulo)
        layout.addView(status)
        layout.addView(botao)
        setContentView(layout)
    }

    private fun ativarGps() {
        if (checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
            return
        }

        val gps = getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

        if (!gps.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            status.text = "Ative a localização do celular"
            return
        }

        status.text = "GPS autorizado e ativado"
    }
}