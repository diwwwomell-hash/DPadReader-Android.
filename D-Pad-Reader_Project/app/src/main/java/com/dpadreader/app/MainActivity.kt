package com.dpadreader.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingExportBytes: ByteArray? = null
    private var pendingExportMime = "application/octet-stream"
    private var pendingExportName = "export.bin"

    companion object {
        private const val REQUEST_OPEN_FILE = 501
        private const val REQUEST_SAVE_FILE = 502
        private const val MAX_EXPORT_BYTES = 40 * 1024 * 1024
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.setSupportZoom(false)
        webView.settings.builtInZoomControls = false
        webView.settings.displayZoomControls = false
        webView.addJavascriptInterface(ExportBridge(), "AndroidDownload")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme ?: return false
                return scheme !in setOf("file", "about", "data", "blob")
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectBlobExportHandler()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                        "text/csv",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/octet-stream"
                    ))
                }
                return try {
                    startActivityForResult(intent, REQUEST_OPEN_FILE)
                    true
                } catch (_: ActivityNotFoundException) {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    Toast.makeText(this@MainActivity, "Tidak ada aplikasi pemilih file.", Toast.LENGTH_LONG).show()
                    false
                }
            }
        }
        webView.loadUrl("file:///android_asset/Index.html")
    }

    private fun injectBlobExportHandler() {
        val script = "(function() {" +
            "if(window.__dpadNativeExportHook)return;window.__dpadNativeExportHook=true;" +
            "document.addEventListener('click',function(event){" +
            "var a=event.target&&event.target.closest?event.target.closest('a[download]'):null;" +
            "if(!a||!a.href||a.href.indexOf('blob:')!==0)return;" +
            "event.preventDefault();event.stopImmediatePropagation();" +
            "var n=a.getAttribute('download')||'D-Pad-Reader-export.bin';" +
            "var b0=window.__dpadBlobs&&window.__dpadBlobs[a.href];(b0?Promise.resolve(b0):fetch(a.href).then(function(r){return r.blob();})).then(function(b){" +
            "var fr=new FileReader();fr.onload=function(){var s=String(fr.result).split(',')[1]||'';" +
            "if(window.AndroidDownload)AndroidDownload.saveFile(n,b.type||'application/octet-stream',s);};" +
            "fr.onerror=function(){alert('Gagal menyiapkan file ekspor.');};fr.readAsDataURL(b);" +
            "}).catch(function(){alert('Gagal membaca file ekspor.');});},true);})();"
        webView.evaluateJavascript(script, null)
    }

    private inner class ExportBridge {
        @JavascriptInterface
        fun saveFile(name: String, mime: String, base64Data: String) {
            try {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                if (bytes.size > MAX_EXPORT_BYTES) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "File ekspor terlalu besar (batas 40 MB).", Toast.LENGTH_LONG).show() }
                    return
                }
                runOnUiThread {
                    pendingExportBytes = bytes
                    pendingExportMime = mime.ifBlank { "application/octet-stream" }
                    pendingExportName = name.substringAfterLast('/').substringAfterLast('\\')
                        .replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "D-Pad-Reader-export.bin" }
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = pendingExportMime
                        putExtra(Intent.EXTRA_TITLE, pendingExportName)
                    }
                    try {
                        startActivityForResult(intent, REQUEST_SAVE_FILE)
                    } catch (_: ActivityNotFoundException) {
                        pendingExportBytes = null
                        Toast.makeText(this@MainActivity, "Tidak dapat membuka dialog penyimpanan.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Gagal memproses file ekspor.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    @Deprecated("Uses activity result API for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_OPEN_FILE -> {
                val cb = fileChooserCallback
                fileChooserCallback = null
                if (resultCode == Activity.RESULT_OK && data?.data != null) cb?.onReceiveValue(arrayOf(data.data!!))
                else cb?.onReceiveValue(null)
            }
            REQUEST_SAVE_FILE -> {
                val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
                val bytes = pendingExportBytes
                pendingExportBytes = null
                if (uri != null && bytes != null) {
                    try {
                        contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                            ?: throw IllegalStateException("Output stream unavailable")
                        Toast.makeText(this, "File berhasil disimpan.", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(this, "Gagal menyimpan file.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::webView.isInitialized) {
            webView.evaluateJavascript(
                "(function(){var b=document.getElementById('btnBack');if(b&&!b.closest('.hidden')){b.click();return true;}return false;})()"
            ) { result -> if (result != "true") super.onBackPressed() }
        } else super.onBackPressed()
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        webView.removeJavascriptInterface("AndroidDownload")
        webView.destroy()
        super.onDestroy()
    }
}
