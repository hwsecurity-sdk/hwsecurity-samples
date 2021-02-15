package de.cotech.hw.fido.example

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import de.cotech.hw.fido.WebViewFidoBridge
import de.cotech.hw.fido.ui.FidoDialogOptions
import de.cotech.hw.fido2.WebViewWebauthnBridge
import de.cotech.hw.fido2.ui.WebauthnDialogOptions

class WebViewFragment : Fragment(), AdapterView.OnItemSelectedListener {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_webview, container, false)

        val spinner = view.findViewById<Spinner>(R.id.spinner)
        val adapter = ArrayAdapter.createFromResource(context!!,
                R.array.website_array, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = this

        webView = view.findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true

        val webauthnOptionsBuilder = WebauthnDialogOptions.builder()
        //webauthnOptionsBuilder.setTheme(R.style.MyDialog);
        //webauthnOptionsBuilder.setFormFactor(WebauthnDialogOptions.FormFactor.SMART_CARD);
//        webauthnOptionsBuilder.setShowSdkLogo(true)
        webauthnOptionsBuilder.setAllowKeyboard(true)
        webauthnOptionsBuilder.setAllowSkipPin(true)
        val webViewWebauthnBridge = WebViewWebauthnBridge
                .createInstanceForWebView(activity as AppCompatActivity, webView, webauthnOptionsBuilder)

        val fidoOptionsBuilder = FidoDialogOptions.builder()
        fidoOptionsBuilder.setShowSdkLogo(true)
        val webViewFidoBridge = WebViewFidoBridge
                .createInstanceForWebView(activity as AppCompatActivity, webView, fidoOptionsBuilder)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?):
                    WebResourceResponse? {
                webViewWebauthnBridge.delegateShouldInterceptRequest(view, request)
                webViewFidoBridge.delegateShouldInterceptRequest(view, request)
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                webViewWebauthnBridge.delegateOnPageStarted(view, url, favicon)
                webViewFidoBridge.delegateOnPageStarted(view, url, favicon)
            }
        }

        val checkForceU2f = view.findViewById<CheckBox>(R.id.checkForceU2f)
        checkForceU2f.setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean -> webViewWebauthnBridge.setForceU2f(checked) }

        val originalUserAgent = webView.settings.userAgentString
        val checkChrome = view.findViewById<CheckBox>(R.id.checkChrome)
        checkChrome.setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->
            if (checked) {
                val chrome83UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.97 Safari/537.36"
                webView.settings.userAgentString = chrome83UserAgent
            } else {
                webView.settings.userAgentString = originalUserAgent
            }
        }

        val firstItem = adapter.getItem(0) as String?
        webView.loadUrl(firstItem)

        return view
    }


    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        val item = parent.getItemAtPosition(pos) as String
        webView.loadUrl(item)
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}

    companion object {
        fun newInstance() = WebViewFragment()
    }
}