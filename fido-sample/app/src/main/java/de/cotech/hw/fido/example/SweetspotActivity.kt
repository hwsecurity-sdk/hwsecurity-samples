package de.cotech.hw.fido.example

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import de.cotech.hw.SecurityKeyCallback
import de.cotech.hw.SecurityKeyManager
import de.cotech.hw.fido.FidoSecurityKey
import de.cotech.hw.fido.FidoSecurityKeyConnectionMode
import java.io.IOException

class SweetspotActivity : AppCompatActivity(), SecurityKeyCallback<FidoSecurityKey?> {
    private lateinit var sweetspotIndicator: ImageView
    private lateinit var nfcFrame: ImageView
    private lateinit var buttonProceed: Button
    var relativeX = 0f
    var relativeY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sweetspot)

        SecurityKeyManager.getInstance().registerCallback(FidoSecurityKeyConnectionMode(), this, this)

        val sweetspotView = findViewById<ConstraintLayout>(R.id.sweetspotView)
        sweetspotIndicator = findViewById(R.id.imageNfcSweetspot)
        nfcFrame = findViewById(R.id.imageNfcFrame)
        buttonProceed = findViewById(R.id.buttonProceed)

        sweetspotView.setOnTouchListener { _: View?, event: MotionEvent ->
            setSweetspotIndicator(event)
            true
        }

        buttonProceed.setOnClickListener { sendEmail() }
    }

    private fun setSweetspotIndicator(event: MotionEvent) {
        val x = event.x
        val y = event.y
        val metrics = DisplayMetrics()
        window.windowManager.defaultDisplay.getMetrics(metrics)
        relativeX = x / metrics.widthPixels
        relativeY = y / metrics.heightPixels
        Log.d("Sweetspot", "selected x = $x, y = $y")
        Log.d("Sweetspot", "selected relativeX = " + relativeX +
                ", relativeY = " + relativeY)
        sweetspotIndicator.post {
            sweetspotIndicator.translationX = x - (sweetspotIndicator.width / 2).toFloat()
            sweetspotIndicator.translationY = y - (sweetspotIndicator.height / 2).toFloat()
            sweetspotIndicator.visibility = View.VISIBLE
            buttonProceed.isEnabled = true
        }
    }

    private fun sendEmail() {
        val addresses = arrayOf("contact@cotech.de")
        val subject = "NFC Evaluation"
        val text = "NFC Sweetspot Data\n\nModel=" + Build.MODEL +
                "\nx=" + relativeX +
                "\ny=" + relativeY +
                "\n\nI am interested in the SDK and like to be contacted: Yes/No (remove one answer)" +
                "\n\nFurther questions:" +
                "\n\n\n"
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("mailto:")
        intent.putExtra(Intent.EXTRA_EMAIL, addresses)
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_TEXT, text)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    override fun onSecurityKeyDiscovered(securityKey: FidoSecurityKey) {
        nfcFrame.visibility = View.VISIBLE
        nfcFrame.postDelayed({ nfcFrame.visibility = View.GONE }, 500)
    }

    override fun onSecurityKeyDiscoveryFailed(exception: IOException) {
        nfcFrame.visibility = View.GONE
    }

    override fun onSecurityKeyDisconnected(securityKey: FidoSecurityKey) {
        nfcFrame.visibility = View.GONE
    }
}